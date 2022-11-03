/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid.data;

import com.google.flatbuffers.FlatBufferBuilder;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.agentdata.AgentDataReporter;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.crash.ApplicationInfo;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.mobile.fbs.HexAgentDataBundle;
import com.newrelic.agent.android.hybrid.StackTrace;
import com.newrelic.agent.android.hybrid.rninterface.IStack;
import com.newrelic.agent.android.hybrid.rninterface.IStackFrame;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataController {
    protected static final AgentConfiguration agentConfiguration = new AgentConfiguration();
    private static final AgentLog log = AgentLogManager.getAgentLog();

    public static FlatBufferBuilder buildAgentDataFromJSError(StackTrace stackTrace, final Map<String, Object> exceptionAttributes) {
        Map<String, Object> jsErrorException = new HashMap<>();
        Map<String, Object> sessionAttributes = new HashMap<>();
        ApplicationInfo applicationInfo = new ApplicationInfo(Agent.getApplicationInformation());

        jsErrorException.put(HexAttribute.HEX_ATTR_APP_VERSION, applicationInfo.getApplicationVersion());
        jsErrorException.put(HexAttribute.HEX_ATTR_APP_BUILD_ID, applicationInfo.getApplicationBuild());
        jsErrorException.put(HexAttribute.HEX_ATTR_JSERROR_NAME, stackTrace.getStackTraceException().getExceptionName());
        jsErrorException.put(HexAttribute.HEX_ATTR_JSERROR_MESSAGE, stackTrace.getStackTraceException().getCause()); //js only has cause here as a message
        jsErrorException.put(HexAttribute.HEX_ATTR_JSERROR_FATAL, stackTrace.isFatal());
        jsErrorException.put(HexAttribute.HEX_ATTR_JSERROR_BUILDID, stackTrace.getBuildId());
        jsErrorException.put(HexAttribute.HEX_ATTR_JSERROR_BUNDLEID, stackTrace.getBuildId()); // bundleId is the same as buildId
        jsErrorException.put(HexAttribute.HEX_ATTR_JSERROR_THREAD, threadSetFromStackElements(stackTrace.getStacks()));

        jsErrorException.putAll(exceptionAttributes);   // will overwrite any of the above with passed attributes

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

        sessionAttributes.putAll(exceptionAttributes);   // will overwrite any of the above with passed attributes

        Set<Map<String, Object>> agentData = new HashSet<>();
        agentData.add(jsErrorException);

        FlatBufferBuilder flat = DataBuilder.startAndFinishAgentData(sessionAttributes, agentData);

        return flat;
    }

    public static FlatBufferBuilder buildAgentDataFromJSError(StackTrace stackTrace) {
        return buildAgentDataFromJSError(stackTrace, new HashMap<String, Object>());
    }

    public static Throwable getRootCause(Throwable throwable) {
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

    public static List<Map<String, Object>> threadSetFromStackElements(IStack[] ste) {
        List<Map<String, Object>> thread = new ArrayList<>();
        IStack stack = ste[0];
        for (IStackFrame ele : stack.getStackFrames()) {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put(HexAttribute.HEX_ATTR_JSERROR_METHOD, ele.getMethodName());
            frame.put(HexAttribute.HEX_ATTR_JSERROR_FILENAME, ele.getFileName());
            frame.put(HexAttribute.HEX_ATTR_JSERROR_LINE_NUMBER, ele.getLineNumber());
            frame.put(HexAttribute.HEX_ATTR_JSERROR_COLUMN, ele.getColumnNumber());
            thread.add(frame);
        }
        return thread;
    }

    public static boolean sendAgentData(StackTrace stackTrace) {
        if (FeatureFlag.featureEnabled(FeatureFlag.HandledExceptions) ||
                FeatureFlag.featureEnabled(FeatureFlag.NativeReporting)) {
            try {
                FlatBufferBuilder flat = buildAgentDataFromJSError(stackTrace);

                final ByteBuffer byteBuffer = flat.dataBuffer().slice();
                final byte[] modifiedBytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(modifiedBytes);
                log.audit(DataBuilder.toJsonString(HexAgentDataBundle.getRootAsHexAgentDataBundle(ByteBuffer.wrap(modifiedBytes)), 0));
                boolean reported = AgentDataReporter.reportAgentData(modifiedBytes);
                if (!reported) {
                    log.error("HandledJSError: exception " + stackTrace.getClass().getName() + " failed to record data.");
                }
                return reported;
            } catch (Exception error) {
                log.error("HandledJSError: exception " + stackTrace.getClass().getName() + " failed to record data.");
            }
        }

        return false;
    }
}
