/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import android.content.Context;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Common mechanics for file-backed caches. Stores one JSON file per entry under
 * {@code filesDir/<dirName>/} to bound per-operation heap cost to a single entry.
 * <p>
 * Subclasses plug in {@link #keyOf(Object)}, {@link #serialize(Object)}, and
 * {@link #deserialize(String, String)}, then forward their public interface methods to the
 * {@code *Internal} template methods below. This class does not implement any public
 * store interface itself so callers with different signatures (e.g.
 * {@code JSErrorStore.store(String, String)}) can adapt as needed.
 */
public abstract class AbstractFileStore<T> {
    protected static final AgentLog log = AgentLogManager.getAgentLog();

    public static final String TMP_SUFFIX = ".tmp";
    public static final String FILE_SUFFIX = ".json";
    /** Sidecar holding the previous entry while {@link #writeAtomic} is mid-overwrite on non-POSIX rename paths. */
    public static final String BAK_SUFFIX = ".bak";

    private static final int MAX_SAFE_ID_LENGTH = 128;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]+");

    protected final File dir;
    protected final int maxCount;
    private final String tag;
    private final String evictMetric;
    private final String corruptMetric;

    protected AbstractFileStore(Context context, String dirName, int maxCount,
                                String evictMetric, String corruptMetric, String tag) {
        this.dir = new File(context.getFilesDir(), dirName);
        this.maxCount = maxCount > 0 ? maxCount : Integer.MAX_VALUE;
        this.evictMetric = evictMetric;
        this.corruptMetric = corruptMetric;
        this.tag = tag;
        ensureDir();
        sweepTransientFiles();
    }

    /** Derive a per-entry key. The returned value is sanitized via {@link #safeFilename(String)} before use. */
    protected abstract String keyOf(T entry);

    /** Serialize an entry to the exact bytes that should land on disk. */
    protected abstract String serialize(T entry);

    /**
     * Parse a file's JSON contents back into an entry. Throw on malformed input.
     * {@code keyFromFile} is the filename (minus {@link #FILE_SUFFIX}), useful when
     * the entry's key was derived from the filename only (e.g. pre-refactor formats
     * that stored only the value in the file body).
     */
    protected abstract T deserialize(String keyFromFile, String json) throws Exception;

    /**
     * Write an entry, evicting the oldest file first if at capacity. Returns false on
     * any IO failure; never throws.
     */
    protected final synchronized boolean storeInternal(T entry) {
        if (entry == null) {
            return false;
        }
        final String rawKey = keyOf(entry);
        if (rawKey == null || rawKey.isEmpty()) {
            log.warn(tag + ".storeInternal: empty key for entry");
            return false;
        }
        try {
            final String safeName = safeFilename(rawKey);
            final File target = new File(dir, safeName + FILE_SUFFIX);
            if (!target.exists()) {
                evictUntilUnderCap();
            }
            return writeAtomic(safeName, serialize(entry));
        } catch (Exception e) {
            log.error(tag + ".storeInternal: ", e);
            return false;
        }
    }

    /**
     * Return every stored entry oldest-first by file mtime. Files that fail to parse
     * are deleted and the corrupt metric (if configured) is incremented; they do not
     * block siblings.
     */
    protected final synchronized List<T> fetchAllInternal() {
        final List<T> result = new ArrayList<>();
        final File[] files = listStoreFiles();
        if (files.length == 0) {
            return result;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            try {
                result.add(parseFile(f));
            } catch (Exception e) {
                log.debug(tag + ": corrupt file [" + f.getName() + "]: " + e);
                if (!f.delete()) {
                    log.debug(tag + ": failed to delete corrupt file [" + f.getName() + "]");
                }
                if (corruptMetric != null) {
                    StatsEngine.get().inc(corruptMetric);
                }
            }
        }
        return result;
    }

    protected final synchronized int countInternal() {
        return listStoreFiles().length;
    }

    protected final synchronized void deleteInternal(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return;
        }
        final File target = new File(dir, safeFilename(rawKey) + FILE_SUFFIX);
        if (target.exists() && !target.delete()) {
            log.debug(tag + ".deleteInternal: failed to delete [" + target.getName() + "]");
        }
    }

    protected final synchronized void clearInternal() {
        final File[] all = dir.listFiles();
        if (all == null) {
            return;
        }
        for (File f : all) {
            if (!f.delete()) {
                log.debug(tag + ".clearInternal: failed to delete [" + f.getName() + "]");
            }
        }
    }

    public final String getRootPath() {
        return dir.getAbsolutePath();
    }

    private void ensureDir() {
        if (!dir.exists() && !dir.mkdirs()) {
            log.error(tag + ": failed to create cache dir [" + dir.getAbsolutePath() + "]");
        }
    }

    /**
     * Drop half-written temp files and recover any in-flight overwrite backups
     * left over from a crash mid-{@link #writeAtomic}.
     *
     * <ul>
     *   <li>{@code .tmp}: half-written body — always discard.</li>
     *   <li>{@code .bak} with a {@code .json} sibling: overwrite succeeded but
     *       cleanup was interrupted — discard the backup.</li>
     *   <li>{@code .bak} without a {@code .json} sibling: overwrite was
     *       interrupted before the new value landed — rename the backup back
     *       into place so the previous entry is preserved.</li>
     * </ul>
     */
    private void sweepTransientFiles() {
        final File[] all = dir.listFiles();
        if (all == null) {
            return;
        }
        for (File f : all) {
            final String name = f.getName();
            if (name.endsWith(TMP_SUFFIX)) {
                if (!f.delete()) {
                    log.debug(tag + ".sweepTransientFiles: failed to delete tmp [" + name + "]");
                }
            } else if (name.endsWith(BAK_SUFFIX)) {
                final String prefix = name.substring(0, name.length() - BAK_SUFFIX.length());
                final File target = new File(dir, prefix + FILE_SUFFIX);
                if (target.exists()) {
                    if (!f.delete()) {
                        log.debug(tag + ".sweepTransientFiles: failed to delete stale bak [" + name + "]");
                    }
                } else if (!f.renameTo(target)) {
                    log.error(tag + ".sweepTransientFiles: failed to restore bak [" + name + "]");
                    if (!f.delete()) {
                        log.debug(tag + ".sweepTransientFiles: failed to delete unrecoverable bak [" + name + "]");
                    }
                }
            }
        }
    }

    private File[] listStoreFiles() {
        final File[] files = dir.listFiles((d, name) -> name.endsWith(FILE_SUFFIX));
        return files == null ? new File[0] : files;
    }

    private void evictUntilUnderCap() {
        if (maxCount == Integer.MAX_VALUE) {
            return;
        }
        File[] files = listStoreFiles();
        if (files.length < maxCount) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int i = 0;
        while (files.length - i >= maxCount && i < files.length) {
            if (files[i].delete()) {
                if (evictMetric != null) {
                    StatsEngine.get().inc(evictMetric);
                }
            } else {
                log.debug(tag + ".evictUntilUnderCap: failed to delete [" + files[i].getName() + "]");
            }
            i++;
        }
    }

    private boolean writeAtomic(String safeName, String json) {
        final File tmp = new File(dir, safeName + TMP_SUFFIX);
        final File target = new File(dir, safeName + FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(tmp, false)) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            log.error(tag + ".writeAtomic: failed writing tmp for [" + safeName + "]: " + e);
            cleanupTmp(tmp, safeName);
            return false;
        }
        // POSIX rename is atomic and replaces an existing target on Android ext4.
        // Try the single-step rename first so we never disturb the old entry unless we
        // know the platform won't overwrite.
        if (tmp.renameTo(target)) {
            return true;
        }
        // Non-POSIX fallback. Move the existing target aside as a backup, then attempt
        // the rename. If anything goes wrong we restore the backup so the overwrite path
        // can never lose the old value, even on partial failure.
        File backup = null;
        if (target.exists()) {
            backup = new File(dir, safeName + BAK_SUFFIX);
            if (backup.exists() && !backup.delete()) {
                log.error(tag + ".writeAtomic: stale backup blocks overwrite for [" + safeName + "]");
                cleanupTmp(tmp, safeName);
                return false;
            }
            if (!target.renameTo(backup)) {
                log.error(tag + ".writeAtomic: cannot move existing target aside for [" + safeName + "]");
                cleanupTmp(tmp, safeName);
                return false;
            }
        }
        if (tmp.renameTo(target)) {
            if (backup != null && !backup.delete()) {
                log.debug(tag + ".writeAtomic: failed to cleanup backup for [" + safeName + "]");
            }
            return true;
        }
        log.error(tag + ".writeAtomic: rename failed for [" + safeName + "]");
        if (backup != null && !backup.renameTo(target)) {
            // In-process restoration failed. The .bak remains on disk so the next
            // sweepTransientFiles() pass at init can still recover the previous entry.
            log.error(tag + ".writeAtomic: failed to restore backup for [" + safeName + "]");
        }
        cleanupTmp(tmp, safeName);
        return false;
    }

    private void cleanupTmp(File tmp, String safeName) {
        if (tmp.exists() && !tmp.delete()) {
            log.debug(tag + ".writeAtomic: failed to cleanup tmp for [" + safeName + "]");
        }
    }

    private T parseFile(File f) throws Exception {
        final String json = Streams.slurpString(f, StandardCharsets.UTF_8.name());
        final String name = f.getName();
        final String key = name.endsWith(FILE_SUFFIX)
                ? name.substring(0, name.length() - FILE_SUFFIX.length())
                : name;
        return deserialize(key, json);
    }

    /**
     * Derive a filesystem-safe filename from any caller-supplied ID. IDs that are
     * already simple ASCII ({@code [A-Za-z0-9._-]+}, &le; 128 chars) pass through
     * unchanged. Anything else is replaced with a SHA-256 hex digest so we never trust
     * untrusted input as a path component.
     */
    public static String safeFilename(String id) {
        if (id.length() <= MAX_SAFE_ID_LENGTH && SAFE_ID.matcher(id).matches()) {
            return id;
        }
        return "h_" + sha256Hex(id);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required on all Android platforms; fall back only to keep the caller alive.
            return Integer.toHexString(s.hashCode());
        }
    }
}