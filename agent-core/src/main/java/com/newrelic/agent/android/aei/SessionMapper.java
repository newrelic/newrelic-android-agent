/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SessionMapper {

    static final Gson gson = new GsonBuilder().create();

    final File mapStore;
    final Map<Integer, String> mapper;

    public SessionMapper(File mapStore) {
        this.mapStore = mapStore;
        this.mapper = new HashMap<>();
        if (mapStore.exists()) {
            load();
        }
    }

    public SessionMapper put(int pid, String sessionId) {
        if (!(sessionId == null || sessionId.isEmpty() || 0 == pid)) {
            mapper.put(pid, sessionId);
        } else {
            AgentLogManager.getAgentLog().debug("Refusing to store null or empty sessionId[" + sessionId + "] for pid[" + pid + "]");
        }

        return this;
    }

    public String get(int pid) {
        return mapper.getOrDefault(pid, null);
    }

    public String getOrDefault(int pid, String defaultSessionId) {
        String sessionId = get(pid);
        return (sessionId == null || sessionId.isEmpty()) ? defaultSessionId : sessionId;
    }

    @SuppressWarnings("unchecked")
    public SessionMapper load() {
        if (mapStore.exists() && mapStore.canRead()) {
            try {
                String storeData = Streams.slurpString(mapStore, StandardCharsets.UTF_8.toString());
                mapper.putAll(gson.fromJson(storeData, Map.class));

            } catch (IOException e) {
                AgentLogManager.getAgentLog().error("Cannot read session ID mapper: " + e);
            }
        } else {
            AgentLogManager.getAgentLog().debug("Cannot read session ID mapper: file does not exist or is unreadable");
        }

        return this;
    }

    public boolean flush() {
        if (mapper.isEmpty()) {
            mapStore.delete();
        } else {
            try (BufferedWriter os = Streams.newBufferedFileWriter(mapStore)) {
                os.write(gson.toJson(mapper));
                os.flush();

            } catch (IOException e) {
                AgentLogManager.getAgentLog().error("Cannot write session ID mapping file: " + e);
            }
        }
        return mapStore.exists() && mapStore.canRead();
    }

    public void clear() {
        mapper.clear();
    }

    public void delete() {
        if (mapStore.exists()) {
            mapStore.delete();
        }
    }

    public void erase(int pid) {
        mapper.remove(pid);
    }

    /**
     * Remove any elements whose key's are *not* in the passed set
     */
    synchronized public void erase(Set<Integer> pidSet) {
        Set<Integer> currentKeySet = mapper.keySet();
        currentKeySet.stream()
                .filter(pid -> !pidSet.contains(pid))
                .collect(Collectors.toSet())
                .forEach(pid -> mapper.remove(pid));
    }

}
