/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TraceHeaderTest {
    TestHeader header;

    @Before
    public void setUp() throws Exception {
        header = new TestHeader();
    }

    @Test
    public void testGetHeaderName() {
        Assert.assertEquals("foo", header.getHeaderName());
    }

    @Test
    public void testGetHeaderValue() {
        Assert.assertEquals("bar@fu-bar", header.getHeaderValue());
    }

    private static class TestHeader implements TraceHeader {

        @Override
        public String getHeaderName() {
            return "foo";
        }

        @Override
        public String getHeaderValue() {
            return "bar@fu-bar";
        }
    }
}