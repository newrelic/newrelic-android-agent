/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import junit.framework.TestCase;

import org.junit.Assert;

public class TraceHeaderTest extends TestCase {
    TestHeader header;

    public void setUp() throws Exception {
        header = new TestHeader();
    }

    public void testGetHeaderName() {
        Assert.assertEquals("foo", header.getHeaderName());
    }

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