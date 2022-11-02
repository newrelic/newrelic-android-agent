/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import android.util.Base64;

public class AndroidEncoder implements Encoder {

    public String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    public String encodeNoWrap(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.DEFAULT | Base64.NO_WRAP);
    }
}
