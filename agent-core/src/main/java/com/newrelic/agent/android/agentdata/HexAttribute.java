/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class HexAttribute {

    public static final String HEX_ATTR_APP_BUILD_ID = "appBuild";
    public static final String HEX_ATTR_APP_VERSION = "appVersion";
    public static final String HEX_ATTR_APP_UUID_HI = "appUuidHigh";
    public static final String HEX_ATTR_APP_UUID_LO = "appUuidLow";

    public static final String HEX_ATTR_SESSION_ID = "sessionId";
    public static final String HEX_ATTR_TIMESTAMP_MS = "timestampMs";

    public static final String HEX_ATTR_MESSAGE = "message";
    public static final String HEX_ATTR_CAUSE = "cause";
    public static final String HEX_ATTR_NAME = "name";
    public static final String HEX_ATTR_THREAD = "thread";
    public static final String HEX_ATTR_CLASS_NAME = "className";
    public static final String HEX_ATTR_METHOD_NAME = "methodName";
    public static final String HEX_ATTR_LINE_NUMBER = "lineNumber";
    public static final String HEX_ATTR_FILENAME = "fileName";

    public static final String HEX_ATTR_THREAD_CRASHED = "crashed";
    public static final String HEX_ATTR_THREAD_STATE = "state";
    public static final String HEX_ATTR_THREAD_NUMBER = "threadNumber";
    public static final String HEX_ATTR_THREAD_ID = "threadId";
    public static final String HEX_ATTR_THREAD_PRI = "priority";

    public static final Set<String> HEX_SESSION_ATTR_WHITELIST = new HashSet<String>(Arrays.asList(
            AnalyticsAttribute.OS_NAME_ATTRIBUTE,
            AnalyticsAttribute.OS_VERSION_ATTRIBUTE,
            AnalyticsAttribute.OS_BUILD_ATTRIBUTE,
            AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE,
            AnalyticsAttribute.DEVICE_MANUFACTURER_ATTRIBUTE,
            AnalyticsAttribute.DEVICE_MODEL_ATTRIBUTE,
            AnalyticsAttribute.UUID_ATTRIBUTE,
            AnalyticsAttribute.CARRIER_ATTRIBUTE,
            AnalyticsAttribute.NEW_RELIC_VERSION_ATTRIBUTE,
            AnalyticsAttribute.MEM_USAGE_MB_ATTRIBUTE,
            AnalyticsAttribute.SESSION_ID_ATTRIBUTE,
            AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE,
            AnalyticsAttribute.APPLICATION_PLATFORM_VERSION_ATTRIBUTE,
            AnalyticsAttribute.RUNTIME_ATTRIBUTE,
            AnalyticsAttribute.ARCHITECTURE_ATTRIBUTE,
            AnalyticsAttribute.APP_BUILD_ATTRIBUTE
    ));

    public static final Set<String> HEX_REQUIRED_ATTRIBUTES = new HashSet<String>(Arrays.asList(
            HexAttribute.HEX_ATTR_APP_BUILD_ID,
            HexAttribute.HEX_ATTR_APP_UUID_HI,
            HexAttribute.HEX_ATTR_APP_UUID_LO,
            HexAttribute.HEX_ATTR_SESSION_ID,
            HexAttribute.HEX_ATTR_MESSAGE,
            HexAttribute.HEX_ATTR_CAUSE,
            HexAttribute.HEX_ATTR_NAME,
            HexAttribute.HEX_ATTR_TIMESTAMP_MS,

            AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE
    ));

}
