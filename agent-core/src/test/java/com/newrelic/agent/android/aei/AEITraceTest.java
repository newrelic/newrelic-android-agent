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

    @Test
    public void decomposeFromSystemTraceWithNull() {
        sysTrace = "AplicationExitInfo(timestamp=16/08/24, 18:01 pid=22767 realUid=10246 packageUid=10246 definingUid=10246 user=0 process=com.newrelic.rpm reason=6 (ANR) subreason=0 (UNKNOWN) status=0 importance=100 pss=264MB rss=444MB description=user request after error: Input dispatching timed out (69322c3 Ventana emergente (server) is not responding. Waited 5000ms for MotionEvent anrTimeLine:2024-08-16 18:01:29.441 seq:377090 eventTime:20824301 deliveryTime:20824304 anrTime:20829304) state=empty trace=null";
        aeiTrace.decomposeFromSystemTrace(sysTrace);
        Assert.assertEquals(0, aeiTrace.threads.size());
        Assert.assertEquals("", aeiTrace.toString());
    }
}