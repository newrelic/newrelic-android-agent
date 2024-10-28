/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.util.Streams;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AEITraceTest {

    private String sysTrace;
    private AEITrace aeiTrace;

    @Before
    public void setUp() throws Exception {
        aeiTrace = new AEITrace();
        sysTrace = Streams.slurpString(AEITraceTest.class.getResource("/applicationExitInfo/systrace").openStream());
    }

    @Test
    public void decomposeFromSystemTrace() {
        aeiTrace.decomposeFromSystemTrace(sysTrace);
        Assert.assertEquals("6295", aeiTrace.pid);
        Assert.assertEquals("2024-10-21 15:48:46.263477197-0700", aeiTrace.createTime);
        Assert.assertEquals(33, aeiTrace.threads.size());
    }
}