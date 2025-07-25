/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.common;

public interface WanType {
    String NONE = "none";
    String WIFI = "wifi";
    String UNKNOWN = "unknown";

    String CDMA = "CDMA";
    String EDGE = "EDGE";
    String EVDO_REV_0 = "EVDO rev 0";
    String EVDO_REV_A = "EVDO rev A";
    String EVDO_REV_B = "EVDO rev B";
    String GPRS = "GPRS";
    String HRPD = "HRPD";
    String HSDPA = "HSDPA";
    String HSPA = "HSPA";
    String HSPAP = "HSPAP";
    String HSUPA = "HSUPA";
    String IDEN = "IDEN";
    String LTE = "LTE";
    String RTT = "1xRTT";
    String UMTS = "UMTS";
    String ETHERNET = "ETHERNET";
}
