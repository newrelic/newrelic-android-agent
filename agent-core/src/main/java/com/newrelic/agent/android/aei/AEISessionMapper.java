/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AEISessionMapper {

    static final Gson gson = new GsonBuilder().create();

    final File mapStore;
    final Map<Integer, AEISessionMeta> mapper;

    public AEISessionMapper(File mapStore) {
        this.mapStore = mapStore;
        this.mapper = new HashMap<>();
        if (mapStore.exists()) {
            load();
        }
    }

    public AEISessionMapper put(int pid, AEISessionMeta model) {
        if (model != null && !(model.sessionId == null || model.sessionId.isEmpty())) {
            mapper.put(pid, model);
        } else {
            AgentLogManager.getAgentLog().debug("Refusing to store null or empty session model for pid[" + pid + "]");
        }

        return this;
    }

    public AEISessionMeta get(int pid) {
        return mapper.getOrDefault(pid, null);
    }

    public String getSessionId(int pid) {
        AEISessionMeta model = get(pid);
        return model == null ? "" : model.sessionId;
    }

    public int getRealAgentID(int pid) {
        AEISessionMeta model = get(pid);
        return model == null ? 0 : model.realAgentId;
    }

    public String getOrDefault(int pid, String defaultSessionId) {
        AEISessionMeta model = get(pid);
        return (model == null || model.sessionId == null || model.sessionId.isEmpty())
                ? defaultSessionId : model.sessionId;
    }

    @SuppressWarnings("unchecked")
    public AEISessionMapper load() {
        if (mapStore.exists() && mapStore.canRead()) {
            try {
                String storeData = Streams.slurpString(mapStore, StandardCharsets.UTF_8.toString());
                final Type gtype = new TypeToken<Map<Integer, AEISessionMeta>>(){}.getType();
                Map map = gson.fromJson(storeData, gtype);

                map.forEach((key, val) -> mapper.putIfAbsent((Integer) key, (AEISessionMeta) val));

            } catch (Exception e) {
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

    public int size() {
        return mapper.size();
    }

    /**
     * Remove any elements whose key's are *not* in the passed set
     */
    synchronized public void erase(Set<Integer> pidSet) {
        Set<Integer> currentKeySet = mapper.keySet();
        currentKeySet.stream()
                .filter(pid -> !pidSet.contains(pid))
                .collect(Collectors.toSet())
                .forEach(mapper::remove);
    }

    public static class AEISessionMeta {
        final String sessionId;
        final int realAgentId;

        public AEISessionMeta(String sessionId, int realAgentId) {
            this.sessionId = sessionId == null ? "" : sessionId;
            this.realAgentId = realAgentId;
        }

        public boolean isValid() {
            return !(sessionId.isEmpty() || realAgentId == 0);
        }
    }
}
