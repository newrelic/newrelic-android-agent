package com.newrelic.agent.android.logging;

import org.junit.Assert;
import org.junit.Test;

public class AgentLogManagerTest {
    @Test
    public void testGetAgentLog() {
        Assert.assertNotNull(AgentLogManager.getAgentLog());
    }

    @Test
    public void testSetAgentLog() {
        AgentLogManager.setAgentLog(null);
        Assert.assertNotNull(AgentLogManager.getAgentLog());

        AgentLogManager.setAgentLog(new DefaultAgentLog());
        Assert.assertTrue(AgentLogManager.getAgentLog() instanceof DefaultAgentLog);
    }
}
