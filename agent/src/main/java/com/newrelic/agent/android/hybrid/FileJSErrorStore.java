/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * File-backed replacement for {@code SharedPrefsJSErrorStore}. Stores one JSON file
 * per JS error under {@code filesDir/nr_jserror_cache/}, which bounds per-operation
 * heap cost to a single error instead of the whole cache (the prior OOM source).
 *
 * On-disk format: a JSON object with {@code id} (original caller-supplied ID) and
 * {@code data} (the JSON payload string — stored verbatim). Filenames are the
 * sanitized/hashed form of the caller's ID; the original ID always lives inside
 * the file so {@code fetchAllEntries()} can recover it regardless of sanitization.
 * Legacy SharedPreferences cache is discarded on upgrade.
 */
public class FileJSErrorStore implements JSErrorStore {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    static final String DIR_NAME = "nr_jserror_cache";
    static final String TMP_SUFFIX = ".tmp";
    static final String FILE_SUFFIX = ".json";

    private static final int MAX_SAFE_ID_LENGTH = 128;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private final File dir;
    private final int maxCount;

    public FileJSErrorStore(Context context, AgentConfiguration config) {
        this.dir = new File(context.getFilesDir(), DIR_NAME);
        final int configured = config.getMaxCachedJsErrorCount();
        this.maxCount = configured > 0 ? configured : AgentConfiguration.DEFAULT_MAX_CACHED_JS_ERROR_COUNT;
        ensureDir();
        sweepTempFiles();
    }

    @Override
    public synchronized boolean store(String id, String data) {
        if (id == null || id.isEmpty()) {
            log.warn("FileJSErrorStore.store: id is null or empty");
            return false;
        }
        if (data == null || data.trim().isEmpty()) {
            log.warn("FileJSErrorStore.store: data is null or empty");
            return false;
        }
        try {
            final String safeName = safeFilenameFor(id);
            final File target = new File(dir, safeName + FILE_SUFFIX);
            if (!target.exists()) {
                evictUntilUnderCap();
            }
            return writeAtomic(safeName, toJsonString(id, data));
        } catch (Exception e) {
            log.error("FileJSErrorStore.store: ", e);
            return false;
        }
    }

    @Override
    public synchronized List<String> fetchAll() {
        return new java.util.ArrayList<>(fetchAllEntriesUnlocked().values());
    }

    /**
     * Returns all stored errors as a single atomic {id → json} snapshot. The
     * in-file {@code id} is authoritative (filenames may have been hashed for
     * safety), so values and keys are guaranteed consistent with each other.
     */
    @Override
    public synchronized Map<String, String> fetchAllEntries() {
        return fetchAllEntriesUnlocked();
    }

    @Override
    public synchronized int count() {
        return listStoreFiles().length;
    }

    @Override
    public synchronized void delete(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        final File target = new File(dir, safeFilenameFor(id) + FILE_SUFFIX);
        if (target.exists() && !target.delete()) {
            log.debug("FileJSErrorStore.delete: failed to delete [" + target.getName() + "]");
        }
    }

    @Override
    public synchronized void clear() {
        final File[] all = dir.listFiles();
        if (all == null) {
            return;
        }
        for (File f : all) {
            if (!f.delete()) {
                log.debug("FileJSErrorStore.clear: failed to delete [" + f.getName() + "]");
            }
        }
    }

    private Map<String, String> fetchAllEntriesUnlocked() {
        final Map<String, String> result = new LinkedHashMap<>();
        final File[] files = listStoreFiles();
        if (files.length == 0) {
            return result;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            try {
                final Map.Entry<String, String> entry = parseFile(f);
                result.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.debug("FileJSErrorStore: corrupt error file [" + f.getName() + "]: " + e);
                if (!f.delete()) {
                    log.debug("FileJSErrorStore: failed to delete corrupt file [" + f.getName() + "]");
                }
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_JS_ERROR_CORRUPTED);
            }
        }
        return result;
    }

    private void ensureDir() {
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("FileJSErrorStore: failed to create cache dir [" + dir.getAbsolutePath() + "]");
        }
    }

    private void sweepTempFiles() {
        final File[] tmps = dir.listFiles((d, name) -> name.endsWith(TMP_SUFFIX));
        if (tmps == null) {
            return;
        }
        for (File f : tmps) {
            if (!f.delete()) {
                log.debug("FileJSErrorStore.sweepTempFiles: failed to delete [" + f.getName() + "]");
            }
        }
    }

    private File[] listStoreFiles() {
        final File[] files = dir.listFiles((d, name) -> name.endsWith(FILE_SUFFIX));
        return files == null ? new File[0] : files;
    }

    private void evictUntilUnderCap() {
        File[] files = listStoreFiles();
        if (files.length < maxCount) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int i = 0;
        while (files.length - i >= maxCount && i < files.length) {
            if (files[i].delete()) {
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_JS_ERROR_EVICTED);
            } else {
                log.debug("FileJSErrorStore.evictUntilUnderCap: failed to delete [" + files[i].getName() + "]");
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
            log.error("FileJSErrorStore.writeAtomic: failed writing tmp for [" + safeName + "]: " + e);
            if (tmp.exists() && !tmp.delete()) {
                log.debug("FileJSErrorStore.writeAtomic: failed to cleanup tmp for [" + safeName + "]");
            }
            return false;
        }
        // POSIX rename is atomic and replaces an existing target on Android ext4.
        // Try the single-step rename first so we never delete the old entry unless we
        // know the platform won't overwrite.
        if (tmp.renameTo(target)) {
            return true;
        }
        if (target.exists() && !target.delete()) {
            log.error("FileJSErrorStore.writeAtomic: cannot replace existing target [" + safeName + "]");
            if (tmp.exists() && !tmp.delete()) {
                log.debug("FileJSErrorStore.writeAtomic: failed to cleanup tmp for [" + safeName + "]");
            }
            return false;
        }
        if (!tmp.renameTo(target)) {
            log.error("FileJSErrorStore.writeAtomic: rename failed for [" + safeName + "]");
            if (tmp.exists() && !tmp.delete()) {
                log.debug("FileJSErrorStore.writeAtomic: failed to cleanup tmp for [" + safeName + "]");
            }
            return false;
        }
        return true;
    }

    private Map.Entry<String, String> parseFile(File f) throws IOException {
        final String json = Streams.slurpString(f, StandardCharsets.UTF_8.name());
        final JsonObject outer = new Gson().fromJson(json, JsonObject.class);
        if (outer == null || !outer.has("id") || !outer.has("data")) {
            throw new IOException("missing id/data fields in " + f.getName());
        }
        final String id = outer.get("id").getAsString();
        final String data = outer.get("data").getAsString();
        return new HashMap.SimpleImmutableEntry<>(id, data);
    }

    private String toJsonString(String id, String data) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("data", data);
        return obj.toString();
    }

    /**
     * Derive a filesystem-safe filename from the caller-supplied ID.
     * UUIDs (the practical case from {@code JSErrorDataController}) pass through
     * unchanged. Anything else — path separators, spaces, non-ASCII, overlong — is
     * replaced with a SHA-256 hex digest so we never trust untrusted input as a
     * path component.
     */
    static String safeFilenameFor(String id) {
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

    public String getRootPath() {
        return dir.getAbsolutePath();
    }
}