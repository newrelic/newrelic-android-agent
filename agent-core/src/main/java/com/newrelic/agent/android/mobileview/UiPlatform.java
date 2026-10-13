/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

/**
 * The {@code uiPlatform} discriminator on MobileView/MobileViewTiming events, per the
 * Mobile Views IDD (NR-356639) canonical schema. Wire values match the cross-agent enum
 * exactly (§5.3) - other agents' values (SwiftUI, ReactNative, Flutter, etc.) are not
 * represented here since this agent never emits them.
 */
public enum UiPlatform {
    ANDROID("Android"),
    ANDROID_FRAGMENT("AndroidFragment"),
    COMPOSE("Compose");

    private final String wireValue;

    UiPlatform(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }
}
