/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid.data;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newrelic.mobile.fbs.ApplicationInfo;
import com.newrelic.mobile.fbs.BoolSessionAttribute;
import com.newrelic.mobile.fbs.DoubleSessionAttribute;
import com.newrelic.mobile.fbs.Framework;
import com.newrelic.mobile.fbs.HexAgentData;
import com.newrelic.mobile.fbs.HexAgentDataBundle;
import com.newrelic.mobile.fbs.LongSessionAttribute;
import com.newrelic.mobile.fbs.Platform;
import com.newrelic.mobile.fbs.StringSessionAttribute;
import com.newrelic.mobile.fbs.jserror.Frame;
import com.newrelic.mobile.fbs.jserror.JsError;
import com.newrelic.mobile.fbs.jserror.Thread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataBuilder {


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
            computeIfAbsent((String) hex.get(HexAttribute.HEX_ATTR_JSERROR_NAME), stringIndexMap, flat);
            computeIfAbsent((String) hex.get(HexAttribute.HEX_ATTR_JSERROR_MESSAGE), stringIndexMap, flat);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> checkedThread = (List<Map<String, Object>>) hex.get(HexAttribute.HEX_ATTR_JSERROR_THREAD);

            thread = checkedThread;
        }

        // For tracking all Thread and Frame offsets in the buffer
        final List<Integer> framesOffsets = new ArrayList<>();
        final List<Integer> threadsOffsets = new ArrayList<>();

        // Create String buffer representations, and store the indexes for the frames
        if (thread != null) {
            for (Map<String, Object> frame : thread) {
                final Map<String, Integer> frameValStringIndexMap = new HashMap<>();

                frameValStringIndexMap.put(HexAttribute.HEX_ATTR_JSERROR_FILENAME, flat.createString(""));

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
                if (frameValStringIndexMap.get(HexAttribute.HEX_ATTR_JSERROR_METHOD) != null) {
                    Frame.addMethod(flat, frameValStringIndexMap.get(HexAttribute.HEX_ATTR_JSERROR_METHOD));
                }
                if (frameValStringIndexMap.get(HexAttribute.HEX_ATTR_JSERROR_FILENAME) != null) {
                    Frame.addFileName(flat, frameValStringIndexMap.get(HexAttribute.HEX_ATTR_JSERROR_FILENAME));
                }
                if (frame.get(HexAttribute.HEX_ATTR_JSERROR_LINE_NUMBER) != null) {
                    Frame.addLineNumber(flat, (Integer) frame.get(HexAttribute.HEX_ATTR_JSERROR_LINE_NUMBER));
                }
                if (frame.get(HexAttribute.HEX_ATTR_JSERROR_COLUMN) != null) {
                    Frame.addColumn(flat, (Integer) frame.get(HexAttribute.HEX_ATTR_JSERROR_COLUMN));
                }

                int frameOffset = Frame.endFrame(flat);
                framesOffsets.add(frameOffset);
            }
        }

        // Create frame vectors and associate with a thread
        int framesOffset = Thread.createFramesVector(flat, toArray(framesOffsets));
        int threadOffset = Thread.createThread(flat, framesOffset);

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

        // Create JSErrors
        final Set<Integer> JSErrorOffsets = new HashSet<>();

        if (!agentData.isEmpty()) {
            for (Map<String, Object> hex : agentData) {
                int nameOffset = stringIndexMapOffset(stringIndexMap, hex.get(HexAttribute.HEX_ATTR_JSERROR_NAME));
                int messageOffset = stringIndexMapOffset(stringIndexMap, hex.get(HexAttribute.HEX_ATTR_JSERROR_MESSAGE));
                boolean fatal = (boolean) (hex.get(HexAttribute.HEX_ATTR_JSERROR_FATAL));
                int buildIdOffset = stringIndexMapOffset(stringIndexMap, hex.get(HexAttribute.HEX_ATTR_JSERROR_BUILDID));
                int bundleIdOffset = stringIndexMapOffset(stringIndexMap, hex.get(HexAttribute.HEX_ATTR_JSERROR_BUNDLEID));

                JsError.startJsError(flat);
                JsError.addFatal(flat, fatal);

                if (-1 != nameOffset) {
                    JsError.addName(flat, nameOffset);
                }
                if (-1 != messageOffset) {
                    JsError.addMessage(flat, messageOffset);
                }
                if (-1 != buildIdOffset) {
                    JsError.addBuildId(flat, buildIdOffset);
                }
                if (-1 != bundleIdOffset) {
                    JsError.addBundleId(flat, bundleIdOffset);
                }

                JsError.addThread(flat, threadOffset);

                int JSErrorOffset = JsError.endJsError(flat);
                JSErrorOffsets.add(JSErrorOffset);
            }
        }

        // Create JSError vector
        int JSErrorVector = -1;

        if (!JSErrorOffsets.isEmpty()) {
            JSErrorVector = HexAgentData.createJsErrorsVector(flat, toArray(JSErrorOffsets));
        }

        ApplicationInfo.startApplicationInfo(flat);
        ApplicationInfo.addPlatform(flat, Platform.Android);
        ApplicationInfo.addFramework(flat, Framework.ReactNative);

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

        if (JSErrorVector != -1) {
            HexAgentData.addJsErrors(flat, JSErrorVector);
        }

        HexAgentData.addApplicationInfo(flat, applicationInfoOffset);

        final Set<Integer> agentDataOffsets = new HashSet<>();

        int agentDataOffset = HexAgentData.endHexAgentData(flat);

        agentDataOffsets.add(agentDataOffset);

        int agentDataVector = HexAgentDataBundle.createHexAgentDataVector(flat, toArray(agentDataOffsets));

        HexAgentDataBundle.startHexAgentDataBundle(flat);

        HexAgentDataBundle.addHexAgentData(flat, agentDataVector);
        int agentDFataBundleOffset = HexAgentDataBundle.endHexAgentDataBundle(flat);
        flat.finish(agentDFataBundleOffset);

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

        for (int si = 0; si < agentData.jsErrorsLength(); si++) {
            final JsError hex = agentData.jsErrors(si);
            map.put(HexAttribute.HEX_ATTR_JSERROR_NAME, hex.name());
            map.put(HexAttribute.HEX_ATTR_JSERROR_MESSAGE, hex.message());
            map.put(HexAttribute.HEX_ATTR_JSERROR_FATAL, hex.fatal());
            map.put(HexAttribute.HEX_ATTR_JSERROR_BUILDID, hex.buildId());
            map.put(HexAttribute.HEX_ATTR_JSERROR_BUNDLEID, hex.bundleId());
            final java.lang.Thread currentThread = java.lang.Thread.currentThread();
            final Map<String, Object> threadMap = new LinkedHashMap<>();
            for (int f = 0; f < hex.thread().framesLength(); f++) {
                final Map<String, Object> frameMap = new LinkedHashMap<>();
                frameMap.put(HexAttribute.HEX_ATTR_JSERROR_FILENAME, hex.thread().frames(f).fileName());
                frameMap.put(HexAttribute.HEX_ATTR_JSERROR_LINE_NUMBER, hex.thread().frames(f).lineNumber());
                frameMap.put(HexAttribute.HEX_ATTR_JSERROR_METHOD, hex.thread().frames(f).method());
                frameMap.put(HexAttribute.HEX_ATTR_JSERROR_COLUMN, hex.thread().frames(f).column());
                threadMap.put("frame " + f, frameMap);
            }

            threadMap.put(HexAttribute.HEX_ATTR_THREAD_CRASHED, false);
            threadMap.put(HexAttribute.HEX_ATTR_THREAD_STATE, currentThread.getState().toString());
            threadMap.put(HexAttribute.HEX_ATTR_THREAD_NUMBER, currentThread.getId());
            threadMap.put(HexAttribute.HEX_ATTR_THREAD_ID, currentThread.getName());
            threadMap.put(HexAttribute.HEX_ATTR_THREAD_PRI, currentThread.getPriority());

            map.put("thread 0", threadMap);
        }
        return map;
    }
}
