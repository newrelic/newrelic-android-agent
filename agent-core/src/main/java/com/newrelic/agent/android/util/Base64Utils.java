/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import java.util.Base64;

/**
 * Base64 helper that produces unwrapped (single-line) output, matching the
 * {@code android.util.Base64.NO_WRAP} convention used elsewhere in the agent.
 * Lives in agent-core so callers in either module can share one encoding.
 */
public final class Base64Utils {

    private Base64Utils() {
    }

    public static String encodeToString(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] decode(String encoded) {
        return Base64.getDecoder().decode(encoded);
    }
}