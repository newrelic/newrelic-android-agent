/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata.builder;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newrelic.agent.android.agentdata.HexAttribute;
import com.newrelic.mobile.fbs.HexAgentData;
import com.newrelic.mobile.fbs.HexAgentDataBundle;
import com.newrelic.mobile.fbs.ApplicationInfo;
import com.newrelic.mobile.fbs.BoolSessionAttribute;
import com.newrelic.mobile.fbs.DoubleSessionAttribute;
import com.newrelic.mobile.fbs.LongSessionAttribute;
import com.newrelic.mobile.fbs.StringSessionAttribute;
import com.newrelic.mobile.fbs.hex.Frame;
import com.newrelic.mobile.fbs.hex.HandledException;
import com.newrelic.mobile.fbs.hex.Thread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentDataBuilder {


    protected static void computeIfAbsent(String s, Map<String, Integer> map, FlatBufferBuilder flat) {
        if (null != s) {
            if (!map.containsValue(s)) {
                int offset = flat.createString(s);
                map.put(s, offset);
            }
        }
    }

    /**
     * @param attributesMap Session attributes to add to AgentData
     * @param agentData     Set of handled exceptions in the form of a Map
     * @return A finished flat buffer
     */
    public static FlatBufferBuilder startAndFinishAgentData(Map<String, Object> attributesMap, Set<Map<String, Object>> agentData) {
        final Map<String, Integer> stringIndexMap = new HashMap<>(); //key: string, value: index/offset

        FlatBufferBuilder flat = new FlatBufferBuilder();

        // Create all string attributes
        for (Map.Entry<String, Object> attribute : attributesMap.entrySet()) {
            final String key = attribute.getKey();
            final Object val = attribute.getValue();

            computeIfAbsent(key, stringIndexMap, flat);
            if (val instanceof String) {
                final String s = (String) val;
                computeIfAbsent(s, stringIndexMap, flat);
            }
        }

        List<Map<String, Object>> thread = null;

        for (Map<String, Object> hex : agentData) {
            computeIfAbsent((String) hex.get(HexAttribute.HEX_ATTR_NAME), stringIndexMap, flat);
            computeIfAbsent((String) hex.get(HexAttribute.HEX_ATTR_MESSAGE), stringIndexMap, flat);
            computeIfAbsent((String) hex.get(HexAttribute.HEX_ATTR_CAUSE), stringIndexMap, flat);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> checkedThread = (List<Map<String, Object>>) hex.get("thread");

            thread = checkedThread;
        }

        // For tracking all Thread and Frame offsets in the buffer
        final List<Integer> framesOffsets = new ArrayList<>();
        final List<Integer> threadsOffsets = new ArrayList<>();

        // Create String buffer representations, and store the indexes for the frames
        if (thread != null) {
            for (Map<String, Object> frame : thread) {
                final Map<String, Integer> frameValStringIndexMap = new HashMap<>();

                frameValStringIndexMap.put(HexAttribute.HEX_ATTR_FILENAME, flat.createString(""));

                for (Map.Entry<String, Object> frameElement : frame.entrySet()) {
                    final String key = frameElement.getKey();
                    final Object val = frameElement.getValue();

                    if (val instanceof String) {
                        int offset = flat.createString((String) val);
                        frameValStringIndexMap.put(key, offset);
                    }
                }

                // Put together frames from stored offsets

                Frame.startFrame(flat);
                if (frameValStringIndexMap.get(HexAttribute.HEX_ATTR_CLASS_NAME) != null) {
                    Frame.addClassName(flat, frameValStringIndexMap.get(HexAttribute.HEX_ATTR_CLASS_NAME));
                }
                if (frameValStringIndexMap.get(HexAttribute.HEX_ATTR_METHOD_NAME) != null) {
                    Frame.addMethodName(flat, frameValStringIndexMap.get(HexAttribute.HEX_ATTR_METHOD_NAME));
                }
                if (frameValStringIndexMap.get(HexAttribute.HEX_ATTR_FILENAME) != null) {
                    Frame.addFileName(flat, frameValStringIndexMap.get(HexAttribute.HEX_ATTR_FILENAME));
                }
                if (frame.get(HexAttribute.HEX_ATTR_LINE_NUMBER) != null) {
                    Frame.addLineNumber(flat, (Integer) frame.get(HexAttribute.HEX_ATTR_LINE_NUMBER));
                }

                int frameOffset = Frame.endFrame(flat);
                framesOffsets.add(frameOffset);
            }
        }

        // Create frame vectors and associate with a thread
        int framesOffset = Thread.createFramesVector(flat, toArray(framesOffsets));
        int threadOffset = Thread.createThread(flat, framesOffset);
        threadsOffsets.add(threadOffset);
        int threadVectorOffset = HandledException.createThreadsVector(flat, toArray(threadsOffsets));

        // Create Attributes
        final Set<Integer> stringSessionAttributes = new HashSet<>();
        final Set<Integer> doubleSessionAttributes = new HashSet<>();
        final Set<Integer> longSessionAttributes = new HashSet<>();
        final Set<Integer> boolSessionAttributes = new HashSet<>();

        for (Map.Entry<String, Object> attribute : attributesMap.entrySet()) {
            final String key = attribute.getKey();
            final int keyIndex = stringIndexMap.get(key);
            final Object val = attribute.getValue();

            if (val instanceof String) {
                stringSessionAttributes.add(StringSessionAttribute.createStringSessionAttribute(flat, keyIndex, stringIndexMap.get(val)));
                continue;
            }

            if (val instanceof Double || val instanceof Float) {
                final Number n = (Number) val;
                doubleSessionAttributes.add(DoubleSessionAttribute.createDoubleSessionAttribute(flat, keyIndex, n.doubleValue()));
                continue;
            }

            if (val instanceof Number) {
                final Number n = (Number) val;
                longSessionAttributes.add(LongSessionAttribute.createLongSessionAttribute(flat, keyIndex, n.longValue()));
                continue;
            }

            if (val instanceof Boolean) {
                boolSessionAttributes.add(BoolSessionAttribute.createBoolSessionAttribute(flat, keyIndex, (Boolean) val));
            }

        }

        // Create attribute vectors
        int stringSessionAttributesVector = -1;
        if (!stringSessionAttributes.isEmpty()) {
            stringSessionAttributesVector = HexAgentData.createStringAttributesVector(flat, toArray(stringSessionAttributes));
        }

        int doubleSessionAttributesVector = -1;
        if (!doubleSessionAttributes.isEmpty()) {
            doubleSessionAttributesVector = HexAgentData.createDoubleAttributesVector(flat, toArray(doubleSessionAttributes));
        }

        int longSessionAttributesVector = -1;
        if (!longSessionAttributes.isEmpty()) {
            longSessionAttributesVector = HexAgentData.createLongAttributesVector(flat, toArray(longSessionAttributes));
        }

        int booleanSessionAttributesVector = -1;
        if (!boolSessionAttributes.isEmpty()) {
            booleanSessionAttributesVector = HexAgentData.createBoolAttributesVector(flat, toArray(boolSessionAttributes));
        }

        // Create Handled Exceptions
        final Set<Integer> handledExceptionOffsets = new HashSet<>();

        if (!agentData.isEmpty()) {
            for (Map<String, Object> hex : agentData) {
                int nameOffset = stringIndexMapOffset(stringIndexMap, hex.get(HexAttribute.HEX_ATTR_NAME));
                int messageOffset = stringIndexMapOffset(stringIndexMap, hex.get(HexAttribute.HEX_ATTR_MESSAGE));
                int causeOffset = stringIndexMapOffset(stringIndexMap, hex.get(HexAttribute.HEX_ATTR_CAUSE));

                long timeStampMs = (long) (hex.containsKey(HexAttribute.HEX_ATTR_TIMESTAMP_MS) ?
                        hex.get(HexAttribute.HEX_ATTR_TIMESTAMP_MS) : System.currentTimeMillis());
                long appUuidHigh = 0L;
                long appUuidLow = 0L;

                try {
                    appUuidHigh = (long) hex.get(HexAttribute.HEX_ATTR_APP_UUID_HI);
                    appUuidLow = (long) hex.get(HexAttribute.HEX_ATTR_APP_UUID_LO);
                } catch (ClassCastException e) {
                    appUuidHigh = 0L;
                    appUuidLow = 0L;
                }

                HandledException.startHandledException(flat);
                HandledException.addAppUuidHigh(flat, appUuidHigh);
                HandledException.addAppUuidLow(flat, appUuidLow);

                if (-1 != timeStampMs) {
                    HandledException.addTimestampMs(flat, timeStampMs);
                }
                if (-1 != nameOffset) {
                    HandledException.addName(flat, nameOffset);
                }
                if (-1 != messageOffset) {
                    HandledException.addMessage(flat, messageOffset);
                }
                if (-1 != causeOffset) {
                    HandledException.addCause(flat, causeOffset);
                }

                HandledException.addThreads(flat, threadVectorOffset);

                int handledExceptionOffset = HandledException.endHandledException(flat);
                handledExceptionOffsets.add(handledExceptionOffset);
            }
        }

        // Create Handled Exception vector
        int handledExceptionVector = -1;

        if (!handledExceptionOffsets.isEmpty()) {
            handledExceptionVector = HexAgentData.createHandledExceptionsVector(flat, toArray(handledExceptionOffsets));
        }

        ApplicationInfo.startApplicationInfo(flat);
        ApplicationInfo.addPlatform(flat, 0);

        int applicationInfoOffset = ApplicationInfo.endApplicationInfo(flat);

        // Finally start the AgentData record and associate the above data with it.
        HexAgentData.startHexAgentData(flat);

        if (stringSessionAttributesVector != -1) {
            HexAgentData.addStringAttributes(flat, stringSessionAttributesVector);
        }

        if (doubleSessionAttributesVector != -1) {
            HexAgentData.addDoubleAttributes(flat, doubleSessionAttributesVector);
        }

        if (longSessionAttributesVector != -1) {
            HexAgentData.addLongAttributes(flat, longSessionAttributesVector);
        }

        if (booleanSessionAttributesVector != -1) {
            HexAgentData.addBoolAttributes(flat, booleanSessionAttributesVector);
        }

        if (handledExceptionVector != -1) {
            HexAgentData.addHandledExceptions(flat, handledExceptionVector);
        }

        HexAgentData.addApplicationInfo(flat, applicationInfoOffset);

        final Set<Integer> agentDataOffsets = new HashSet<>();

        int agentDataOffset = HexAgentData.endHexAgentData(flat);

        agentDataOffsets.add(agentDataOffset);

        int agentDataVector = HexAgentDataBundle.createHexAgentDataVector(flat, toArray(agentDataOffsets));

        HexAgentDataBundle.startHexAgentDataBundle(flat);

        HexAgentDataBundle.addHexAgentData(flat, agentDataVector);
        int agentDataBundleOffset = HexAgentDataBundle.endHexAgentDataBundle(flat);
        flat.finish(agentDataBundleOffset);

        return flat;

    }

    private static int stringIndexMapOffset(Map<String, Integer> stringIndexMap, Object hexKey) {
        Integer offset = -1;
        if (hexKey != null) {
            Integer index = stringIndexMap.get(hexKey);
            if (index != null) {
                offset = index;
            }
        }
        return offset.intValue();
    }

    private static int[] toArray(Collection<Integer> c) {
        final int[] a = new int[c.size()];
        final Iterator<Integer> i = c.iterator();
        int index = 0;
        while (i.hasNext()) {
            a[index++] = i.next();
        }
        return a;
    }

    public static String toJsonString(HexAgentDataBundle agentDataBundle, int index) {
        Gson gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .serializeNulls()
                .setPrettyPrinting()
                .create();

        HexAgentData agentData = agentDataBundle.hexAgentData(index);
        return gson.toJson(attributesMapFromAgentData(agentData));
    }

    public static Map<String, Object> attributesMapFromAgentData(final HexAgentData agentData) {
        final Map<String, Object> map = new HashMap<>();

        for (int si = 0; si < agentData.stringAttributesLength(); si++) {
            final StringSessionAttribute a = agentData.stringAttributes(si);
            map.put(a.name(), a.value());
        }

        for (int si = 0; si < agentData.longAttributesLength(); si++) {
            final LongSessionAttribute a = agentData.longAttributes(si);
            map.put(a.name(), a.value());
        }

        for (int si = 0; si < agentData.doubleAttributesLength(); si++) {
            final DoubleSessionAttribute a = agentData.doubleAttributes(si);
            map.put(a.name(), a.value());
        }

        for (int si = 0; si < agentData.boolAttributesLength(); si++) {
            final BoolSessionAttribute a = agentData.boolAttributes(si);
            map.put(a.name(), a.value());
        }

        for (int si = 0; si < agentData.handledExceptionsLength(); si++) {
            final HandledException hex = agentData.handledExceptions(si);
            map.put(HexAttribute.HEX_ATTR_TIMESTAMP_MS, hex.timestampMs());
            map.put(HexAttribute.HEX_ATTR_APP_UUID_HI, hex.appUuidHigh());
            map.put(HexAttribute.HEX_ATTR_APP_UUID_LO, hex.appUuidLow());
            map.put(HexAttribute.HEX_ATTR_NAME, hex.name());
            map.put(HexAttribute.HEX_ATTR_CAUSE, hex.cause());
            map.put(HexAttribute.HEX_ATTR_MESSAGE, hex.message());
            for (int t = 0; t < hex.threadsLength(); t++) {
                final java.lang.Thread currentThread = java.lang.Thread.currentThread();
                final Map<String, Object> threadMap = new LinkedHashMap<>();
                for (int f = 0; f < hex.threads(t).framesLength(); f++) {
                    final Map<String, Object> frameMap = new LinkedHashMap<>();
                    frameMap.put(HexAttribute.HEX_ATTR_FILENAME, hex.threads(t).frames(f).fileName());
                    frameMap.put(HexAttribute.HEX_ATTR_LINE_NUMBER, hex.threads(t).frames(f).lineNumber());
                    frameMap.put(HexAttribute.HEX_ATTR_CLASS_NAME, hex.threads(t).frames(f).className());
                    frameMap.put(HexAttribute.HEX_ATTR_METHOD_NAME, hex.threads(t).frames(f).methodName());
                    threadMap.put("frame " + f, frameMap);
                }

                threadMap.put(HexAttribute.HEX_ATTR_THREAD_CRASHED, false);
                threadMap.put(HexAttribute.HEX_ATTR_THREAD_STATE, currentThread.getState().toString());
                threadMap.put(HexAttribute.HEX_ATTR_THREAD_NUMBER, currentThread.getId());
                threadMap.put(HexAttribute.HEX_ATTR_THREAD_ID, currentThread.getName());
                threadMap.put(HexAttribute.HEX_ATTR_THREAD_PRI, currentThread.getPriority());

                map.put("thread " + t, threadMap);
            }
        }
        return map;
    }
}
