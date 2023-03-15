/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.retrofit;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import retrofit.mime.TypedInput;

public class EmptyBodyTypedInput implements TypedInput {
    @Override
    public String mimeType() {
        return "text/html;charset=utf-8";
    }

    @Override
    public long length() {
        return 0;
    }

    @Override
    public InputStream in() throws IOException {
        return new ByteArrayInputStream(new byte[0]);
    }

}
