/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class AgentBuildIdTest {

    @After
    public void tearDown() {
        Agent.setBuildId(null);
    }

    @Test
    public void setBuildIdTakesPriorityOverReflectionFallback() {
        Agent.setBuildId("pushed-build-id-123");
        assertEquals("pushed-build-id-123", Agent.getBuildId());
    }

    @Test
    public void fallsBackToReflectionWhenNotPushed() {
        // No setBuildId() call: falls back to reflecting the NewRelicConfig test fixture
        // at agent-core/src/test/java/com/newrelic/agent/android/NewRelicConfig.java
        String buildId = Agent.getBuildId();
        assertFalse(buildId.isEmpty());
        assertTrue(buildId.matches("[0-9a-fA-F-]{36}"));
    }
}