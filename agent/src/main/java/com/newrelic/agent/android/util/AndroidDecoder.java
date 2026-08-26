/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import android.util.Base64;

public class AndroidDecoder implements Decoder {

    public byte[] decode(String bytes) {
        return Base64.decode(bytes, Base64.DEFAULT);
    }

    public byte[] decodeNoWrap(String bytes) {
        return Base64.decode(bytes, Base64.DEFAULT | Base64.NO_WRAP);
    }
}
