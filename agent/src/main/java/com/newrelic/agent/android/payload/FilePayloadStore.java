/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import android.content.Context;
import android.util.Base64;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * File-backed replacement for {@code SharedPrefsPayloadStore}. Stores one JSON file
 * per payload under {@code filesDir/nr_payload_cache/}, which bounds per-operation
 * heap cost to a single payload instead of the whole cache (the prior OOM source).
 *
 * On-disk format: a JSON object with {@code payload} (nested metadata object with
 * {@code uuid} and {@code timestamp}) and {@code encodedPayload} (Base64 bytes).
 * Legacy SharedPreferences cache is discarded on upgrade.
 */
public class FilePayloadStore implements PayloadStore<Payload> {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    static final String DIR_NAME = "nr_payload_cache";
    static final String TMP_SUFFIX = ".tmp";
    static final String FILE_SUFFIX = ".json";

    private final File dir;
    private final int maxCount;

    public FilePayloadStore(Context context, AgentConfiguration config) {
        this.dir = new File(context.getFilesDir(), DIR_NAME);
        final int configured = config.getMaxCachedPayloadCount();
        this.maxCount = configured > 0 ? configured : AgentConfiguration.DEFAULT_MAX_CACHED_PAYLOAD_COUNT;
        ensureDir();
        sweepTempFiles();
    }

    @Override
    public synchronized boolean store(Payload payload) {
        if (payload == null || payload.getUuid() == null) {
            return false;
        }
        try {
            final String uuid = payload.getUuid();
            final File target = new File(dir, uuid + FILE_SUFFIX);
            if (!target.exists()) {
                evictUntilUnderCap();
            }
            return writeAtomic(uuid, toJsonString(payload));
        } catch (Exception e) {
            log.error("FilePayloadStore.store: ", e);
            return false;
        }
    }

    @Override
    public synchronized List<Payload> fetchAll() {
        final List<Payload> result = new ArrayList<>();
        final File[] files = listPayloadFiles();
        if (files.length == 0) {
            return result;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            try {
                result.add(parseFile(f));
            } catch (Exception e) {
                log.debug("FilePayloadStore: corrupt payload file [" + f.getName() + "]: " + e);
                if (!f.delete()) {
                    log.debug("FilePayloadStore: failed to delete corrupt file [" + f.getName() + "]");
                }
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_PAYLOAD_CORRUPTED);
            }
        }
        return result;
    }

    @Override
    public synchronized int count() {
        return listPayloadFiles().length;
    }

    @Override
    public synchronized void delete(Payload payload) {
        if (payload == null || payload.getUuid() == null) {
            return;
        }
        final File target = new File(dir, payload.getUuid() + FILE_SUFFIX);
        if (target.exists() && !target.delete()) {
            log.debug("FilePayloadStore.delete: failed to delete [" + target.getName() + "]");
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
                log.debug("FilePayloadStore.clear: failed to delete [" + f.getName() + "]");
            }
        }
    }

    private void ensureDir() {
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("FilePayloadStore: failed to create cache dir [" + dir.getAbsolutePath() + "]");
        }
    }

    private void sweepTempFiles() {
        final File[] tmps = dir.listFiles((d, name) -> name.endsWith(TMP_SUFFIX));
        if (tmps == null) {
            return;
        }
        for (File f : tmps) {
            if (!f.delete()) {
                log.debug("FilePayloadStore.sweepTempFiles: failed to delete [" + f.getName() + "]");
            }
        }
    }

    private File[] listPayloadFiles() {
        final File[] files = dir.listFiles((d, name) -> name.endsWith(FILE_SUFFIX));
        return files == null ? new File[0] : files;
    }

    private void evictUntilUnderCap() {
        File[] files = listPayloadFiles();
        if (files.length < maxCount) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int i = 0;
        while (files.length - i >= maxCount && i < files.length) {
            if (files[i].delete()) {
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_PAYLOAD_EVICTED);
            } else {
                log.debug("FilePayloadStore.evictUntilUnderCap: failed to delete [" + files[i].getName() + "]");
            }
            i++;
        }
    }

    private boolean writeAtomic(String uuid, String json) {
        final File tmp = new File(dir, uuid + TMP_SUFFIX);
        final File target = new File(dir, uuid + FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(tmp, false)) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            log.error("FilePayloadStore.writeAtomic: failed writing tmp for [" + uuid + "]: " + e);
            if (tmp.exists() && !tmp.delete()) {
                log.debug("FilePayloadStore.writeAtomic: failed to cleanup tmp for [" + uuid + "]");
            }
            return false;
        }
        // POSIX rename is atomic and replaces an existing target on Android ext4.
        // Try the single-step rename first so we never delete the old payload unless we
        // know the platform won't overwrite.
        if (tmp.renameTo(target)) {
            return true;
        }
        if (target.exists() && !target.delete()) {
            log.error("FilePayloadStore.writeAtomic: cannot replace existing target [" + uuid + "]");
            if (tmp.exists() && !tmp.delete()) {
                log.debug("FilePayloadStore.writeAtomic: failed to cleanup tmp for [" + uuid + "]");
            }
            return false;
        }
        if (!tmp.renameTo(target)) {
            log.error("FilePayloadStore.writeAtomic: rename failed for [" + uuid + "]");
            if (tmp.exists() && !tmp.delete()) {
                log.debug("FilePayloadStore.writeAtomic: failed to cleanup tmp for [" + uuid + "]");
            }
            return false;
        }
        return true;
    }

    private Payload parseFile(File f) throws IOException {
        final String json = Streams.slurpString(f, StandardCharsets.UTF_8.name());
        final JsonObject outer = new Gson().fromJson(json, JsonObject.class);
        if (outer == null || !outer.has("payload") || !outer.has("encodedPayload")) {
            throw new IOException("missing payload fields in " + f.getName());
        }
        final JsonObject meta = outer.getAsJsonObject("payload");
        if (meta == null || !meta.has("uuid") || !meta.has("timestamp")) {
            throw new IOException("invalid payload metadata in " + f.getName());
        }
        final Payload payload = new Payload();
        payload.uuid = meta.get("uuid").getAsString();
        payload.timestamp = meta.get("timestamp").getAsLong();
        payload.putBytes(decodeStringToBytes(outer.get("encodedPayload").getAsString()));
        return payload;
    }

    private String toJsonString(Payload payload) {
        final JsonObject obj = new JsonObject();
        obj.add("payload", payload.asJsonObject());
        obj.addProperty("encodedPayload", encodeBytes(payload.getBytes()));
        return obj.toString();
    }

    private static String encodeBytes(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] decodeStringToBytes(String encoded) {
        return Base64.decode(encoded, Base64.DEFAULT);
    }
}