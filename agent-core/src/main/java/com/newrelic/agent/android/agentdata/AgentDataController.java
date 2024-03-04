/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.google.flatbuffers.FlatBufferBuilder;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.agentdata.builder.AgentDataBuilder;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.crash.Crash;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.crash.ApplicationInfo;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.ExceptionHelper;
import com.newrelic.mobile.fbs.HexAgentDataBundle;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AgentDataController {
    protected static final AgentConfiguration agentConfiguration = new AgentConfiguration();
    private static final AgentLog log = AgentLogManager.getAgentLog();

    //build agent data using passed attributes
    static FlatBufferBuilder buildAgentDataFromHandledException(Throwable e, final Map<String, Object> exceptionAttributes) {
        Map<String, Object> handledException = new HashMap<>();
        Map<String, Object> sessionAttributes = new HashMap<>();
        ApplicationInfo applicationInfo = new ApplicationInfo(Agent.getApplicationInformation());

        UUID buildUuid;
        try {
            buildUuid = UUID.fromString(Crash.getSafeBuildId()); //this is for ease of accessing high and low bits
        } catch (IllegalArgumentException ie) {
            buildUuid = UUID.randomUUID();
            ExceptionHelper.recordSupportabilityMetric(ie, "RandomUUID");
        }

        handledException.put(HexAttribute.HEX_ATTR_APP_UUID_HI, buildUuid.getLeastSignificantBits());
        handledException.put(HexAttribute.HEX_ATTR_APP_UUID_LO, buildUuid.getMostSignificantBits());
        handledException.put(HexAttribute.HEX_ATTR_APP_VERSION, applicationInfo.getApplicationVersion());
        handledException.put(HexAttribute.HEX_ATTR_APP_BUILD_ID, applicationInfo.getApplicationBuild());
        handledException.put(HexAttribute.HEX_ATTR_SESSION_ID, agentConfiguration.getSessionID());
        handledException.put(HexAttribute.HEX_ATTR_TIMESTAMP_MS, System.currentTimeMillis());
        handledException.put(HexAttribute.HEX_ATTR_MESSAGE, e.getMessage());
        handledException.put(HexAttribute.HEX_ATTR_CAUSE, getRootCause(e).toString());
        handledException.put(HexAttribute.HEX_ATTR_NAME, e.getClass().toString());
        handledException.put(HexAttribute.HEX_ATTR_THREAD, threadSetFromStackElements(e.getStackTrace()));

        handledException.putAll(exceptionAttributes);   // will overwrite any of the above with passed attributes

        for (AnalyticsAttribute attribute : AnalyticsControllerImpl.getInstance().getSessionAttributes()) {
            switch (attribute.getAttributeDataType()) {
                case STRING:
                    sessionAttributes.put(attribute.getName(), attribute.getStringValue());
                    break;
                case DOUBLE:
                    sessionAttributes.put(attribute.getName(), attribute.getDoubleValue());
                    break;
                case BOOLEAN:
                    sessionAttributes.put(attribute.getName(), attribute.getBooleanValue());
                    break;
            }
        }

        // add timeSinceLoad attribute, included in other events
        long sessionDuration = Agent.getImpl().getSessionDurationMillis();
        if (Harvest.INVALID_SESSION_DURATION == sessionDuration) {
            log.error("Harvest instance is not running! Session duration will be invalid");
        } else {
            sessionAttributes.put(AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE, sessionDuration / 1000.00f);
        }
        sessionAttributes.put("obfuscated", Agent.getIsObfuscated());
        sessionAttributes.putAll(exceptionAttributes);   // will overwrite any of the above with passed attributes

        Set<Map<String, Object>> agentData = new HashSet<>();
        agentData.add(handledException);

        FlatBufferBuilder flat = AgentDataBuilder.startAndFinishAgentData(sessionAttributes, agentData);

        return flat;
    }

    //build agent data
    static FlatBufferBuilder buildAgentDataFromHandledException(Throwable e) {
        return buildAgentDataFromHandledException(e, new HashMap<String, Object>());
    }

    protected static Throwable getRootCause(Throwable throwable) {
        try {
            if (throwable != null) {
                final Throwable cause = throwable.getCause();

                if (cause == null) {
                    return throwable;
                } else {
                    return getRootCause(cause);
                }
            }
        } catch (Exception e) {
            // RuntimeException thrown on: Duplicate found in causal chain so cropping to prevent loop
            if (throwable != null) {
                return throwable;       // return the last known throwable
            }
        }

        return new Throwable("Unknown cause");
    }

    protected static List<Map<String, Object>> threadSetFromStackElements(StackTraceElement[] ste) {
        List<Map<String, Object>> thread = new ArrayList<>();
        for (StackTraceElement ele : ste) {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put(HexAttribute.HEX_ATTR_CLASS_NAME, ele.getClassName());
            frame.put(HexAttribute.HEX_ATTR_METHOD_NAME, ele.getMethodName());
            frame.put(HexAttribute.HEX_ATTR_LINE_NUMBER, ele.getLineNumber());
            frame.put(HexAttribute.HEX_ATTR_FILENAME, ele.getFileName());
            thread.add(frame);
        }
        return thread;
    }

    //send agent data in bytes[]
    public static boolean sendAgentData(Throwable e, Map<String, Object> attributes) {
        if (FeatureFlag.featureEnabled(FeatureFlag.HandledExceptions) ||
                FeatureFlag.featureEnabled(FeatureFlag.NativeReporting)) {
            try {
                //Offline Storage
                if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
                    if (!Agent.hasReachableNetworkConnection(null)) {
                        attributes.put(AnalyticsAttribute.OFFLINE_ATTRIBUTE_NAME, true);
                    }
                }

                FlatBufferBuilder flat = buildAgentDataFromHandledException(e, attributes);

                final ByteBuffer byteBuffer = flat.dataBuffer().slice();
                final byte[] modifiedBytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(modifiedBytes);
                log.audit(AgentDataBuilder.toJsonString(HexAgentDataBundle.getRootAsHexAgentDataBundle(ByteBuffer.wrap(modifiedBytes)), 0));
                boolean reported = AgentDataReporter.reportAgentData(modifiedBytes);
                if (!reported) {
                    log.error("HandledException: exception " + e.getClass().getName() + " failed to record data.");
                }
                return reported;
            } catch (Exception error) {
                log.error("HandledException: exception " + e.getClass().getName() + " failed to record data.");
            }
        }

        return false;
    }
}
