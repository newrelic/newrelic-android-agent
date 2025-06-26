/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.common;

public interface WanType {
    public static final String NONE = "none";
    public static final String WIFI = "wifi";
    public static final String UNKNOWN = "unknown";

    public static final String CDMA = "CDMA";
    public static final String EDGE = "EDGE";
    public static final String EVDO_REV_0 = "EVDO rev 0";
    public static final String EVDO_REV_A = "EVDO rev A";
    public static final String EVDO_REV_B = "EVDO rev B";
    public static final String GPRS = "GPRS";
    public static final String HRPD = "HRPD";
    public static final String HSDPA = "HSDPA";
    public static final String HSPA = "HSPA";
    public static final String HSPAP = "HSPAP";
    public static final String HSUPA = "HSUPA";
    public static final String IDEN = "IDEN";
    public static final String LTE = "LTE";
    public static final String RTT = "1xRTT";
    public static final String UMTS = "UMTS";
    public static final String ETHERNET = "ETHERNET";
}
