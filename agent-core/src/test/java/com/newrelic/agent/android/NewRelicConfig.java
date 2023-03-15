/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import java.util.UUID;

public final class NewRelicConfig {
    static final String VERSION = "6.00.0";
    static final String BUILD_ID = UUID.randomUUID().toString();
    static final Boolean OBFUSCATED = true;

    NewRelicConfig() {
    }

    public static String getBuildId() {
        return BUILD_ID;
    }
}

