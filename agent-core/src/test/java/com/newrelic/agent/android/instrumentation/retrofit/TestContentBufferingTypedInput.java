/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.retrofit;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import retrofit.mime.TypedInput;

public class TestContentBufferingTypedInput {

    public class NullTypedInput implements TypedInput {
        @Override
        public String mimeType() {
            return "blarg";
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public InputStream in() throws IOException {
            return null;
        }
    }

    public class EmptyTypedInput implements TypedInput {
        @Override
        public String mimeType() {
            return "blarg";
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

    public class TestTypedInput implements TypedInput {
        public final static String body = "This is a test.";

        @Override
        public String mimeType() {
            return "blarg";
        }

        @Override
        public long length() {
            return body.length();
        }

        @Override
        public InputStream in() throws IOException {
            return new ByteArrayInputStream(body.getBytes());
        }
    }

    @Test
    public void testLength() throws Exception {
        ContentBufferingTypedInput cbti = new ContentBufferingTypedInput(new NullTypedInput());
        assertNotNull(cbti.in());
        assertEquals(0, cbti.length());

        cbti = new ContentBufferingTypedInput(new NullTypedInput());
        assertNotNull(cbti.in());
        assertEquals(0, cbti.length());

        cbti = new ContentBufferingTypedInput(new TestTypedInput());
        assertNotNull(cbti.in());
        assertEquals(TestTypedInput.body.length(), cbti.length());
    }

    @Test
    public void testNullRetrofitBody() throws Exception {
        ContentBufferingTypedInput cbti = new ContentBufferingTypedInput(new NullTypedInput());
        assertNotNull(cbti.in());
        assertEquals(0, cbti.length());
    }

    @Test
    public void testEmptyRetrofitBody() throws Exception {
        ContentBufferingTypedInput cbti = new ContentBufferingTypedInput(new EmptyTypedInput());
        assertNotNull(cbti.in());
        assertEquals(0, cbti.length());
    }

}
