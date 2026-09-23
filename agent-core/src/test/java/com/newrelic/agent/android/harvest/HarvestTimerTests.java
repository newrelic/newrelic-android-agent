/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HarvestTimerTests {
    private final AgentLog log = AgentLogManager.getAgentLog();

    @BeforeClass
    public static void setLogging() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        ApplicationStateMonitor.getInstance().activityStarted();
    }

    @Test
    public void testHarvestStartStop() {
        TestHarvestTimer timer = new TestHarvestTimer();

        timer.setPeriod(1000);
        timer.start();
        Assert.assertTrue(timer.isRunning());
        try {
            // Timer threads waits a period before waking up,
            // so wait at least two periods
            Thread.sleep(timer.getPeriod() * 2);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
        timer.stop();
        Assert.assertFalse(timer.isRunning());
        Assert.assertTrue("Should run at least twice", 2 <= timer.getRuns());
    }

    @Test
    public void testWontHarvestAtInsanePeriods() {
        TestHarvestTimer timer = new TestHarvestTimer();
        timer.setPeriod(0);
        timer.start();
        Assert.assertFalse(timer.isRunning());
        timer.setPeriod(-1);
        timer.start();
        Assert.assertFalse(timer.isRunning());
    }

    @Test
    public void testWontSkipLateHarvests() {
        SlowHarvestTimer timer = new SlowHarvestTimer();

        timer.setPeriod(100);
        timer.setTickDelay(1000);
        timer.start();
        Assert.assertTrue(timer.isRunning());
        try {
            // Timer threads waits a period before waking up,
            // so wait at least two periods
            Thread.sleep((timer.getPeriod() * 2) + timer.getTickDelay());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        timer.stop();
        Assert.assertFalse(timer.isRunning());
        Assert.assertTrue("Should run at least twice", 2 <= timer.getRuns());
    }

    @Test
    public void testWontHarvestEarly() {
        TestHarvestTimer timer = new TestHarvestTimer();

        timer.setPeriod(2000);
        timer.start();
        Assert.assertTrue(timer.isRunning());

        // Manually run the timer to generate early ticks
        timer.run();
        timer.run();
        timer.run();
        timer.run();
        timer.stop();
        Assert.assertFalse(timer.isRunning());
        Assert.assertEquals(1, timer.getTicks());
    }

    @Test
    public void testStopAndRestart() {
        TestHarvestTimer timer = new TestHarvestTimer();
        timer.setPeriod(200);
        timer.start();
        Assert.assertTrue(timer.isRunning());
        try {
            Thread.sleep(timer.getPeriod() * 2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Assert.assertTrue(timer.isRunning());

        long runs = timer.getRuns();
        long ticks = timer.getTicks();

        Assert.assertTrue(runs > 0);
        Assert.assertTrue(ticks > 0);
        timer.stop();
        Assert.assertFalse(timer.isRunning());

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        timer.start();
        Assert.assertTrue(timer.isRunning());

        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Assert.assertTrue(timer.getRuns() >= runs);
        Assert.assertTrue(timer.getTicks() >= ticks);
        timer.stop();
    }

    @Test
    public void testDisablingHarvesterStopsTimer() {
        TestHarvestTimer timer = new TestHarvestTimer();

        timer.setPeriod(10);
        timer.start();
        Assert.assertTrue(timer.isRunning());
        timer.disableHarvester();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Assert.assertFalse(timer.isRunning());
    }

    // @Test
    public void testWithLocalCollector() {
        Harvester harvester = createTestHarvester("AAa29aa59833841c861916f82cc3fb74011681fec3", "localhost:9080");
        TestHarvestTimer timer = new TestHarvestTimer(harvester);

        timer.setPeriod(1000);
        timer.start();
        try {
            Thread.sleep(timer.getPeriod() * 10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        timer.stop();
        Assert.assertTrue(timer.getTicks() > 0);
    }

    @Test
    public void tickNowShouldHarvestEvenWhileAppIsInBackground() {
        // NR-614098 made ApplicationStateMonitor.isAppInBackground() accurate at the exact
        // moment AndroidAgentImpl.applicationBackgrounded() runs. That callback calls
        // Harvest.harvestNow(true, true) -> HarvestTimer.tickNow(true) to flush any remaining
        // events before shutdown. tickNow() is a deliberate, explicit harvest (it already
        // disregards the "time since last tick" limit) and must not be blocked by the
        // periodic-scheduler's background guard, or queued events are silently dropped.
        FeatureFlag.disableFeature(FeatureFlag.BackgroundReporting);

        TestHarvestTimer timer = new TestHarvestTimer();
        timer.setPeriod(60000);
        timer.start();

        // HarvestTimer.start() schedules an immediate tick (delay=0) while still foregrounded;
        // let it settle before establishing our baseline, so we only measure the tickNow() call below.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
        long executeCountBeforeBackgrounding = timer.getExecuteCount();

        ApplicationStateMonitor.getInstance().uiHidden();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertTrue("Precondition: app must be in background", ApplicationStateMonitor.isAppInBackground());

        timer.tickNow(true);

        Assert.assertEquals("tickNow() must harvest even while app is in background",
                executeCountBeforeBackgrounding + 1, timer.getExecuteCount());

        timer.stop();
        ApplicationStateMonitor.getInstance().activityStarted();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void shouldCancelFutureTasks() throws Exception {
        TestHarvestTimer timer = new TestHarvestTimer();
        timer.setPeriod(10);
        timer.start();

        Assert.assertTrue(timer.isRunning());
        timer.tickNow(true);
        timer.cancelPendingTasks();
        Assert.assertFalse(timer.isRunning());

        timer.start();
        Assert.assertTrue(timer.isRunning());
        timer.tickNow(true);
        timer.cancelPendingTasks();
        Assert.assertFalse(timer.isRunning());

    }

    class TestHarvestTimer extends HarvestTimer {
        private long runs = 0;
        private long ticks = 0;

        public TestHarvestTimer() {
            super(new MockHarvester());
        }

        public TestHarvestTimer(Harvester harvester) {
            super(harvester);
        }

        @Override
        public void run() {
            log.info("TestHarvestTimer: run" + runs);
            runs++;
            super.run();
        }

        public long getRuns() {
            return runs;
        }

        public long getTicks() {
            return ticks;
        }

        public void tick() {
            log.info("TestHarvestTimer: tick");
            super.tick();
            ticks++;
        }

        public void disableHarvester() {
            ((MockHarvester) harvester).setDisabled(true);
        }

        public long getExecuteCount() {
            return ((MockHarvester) harvester).getExecuteCount();
        }

        @Override
        protected void cancelPendingTasks() {
            super.cancelPendingTasks();
        }

        public long getPeriod() {
            return this.period;
        }
    }

    class SlowHarvestTimer extends TestHarvestTimer {
        private long tickDelay;

        public void tick() {
            try {
                Thread.sleep(tickDelay);
            } catch (InterruptedException e) {
                // This probably will happen, if the delay is really long.
            }
        }

        void setTickDelay(long tickDelay) {
            this.tickDelay = tickDelay;
        }

        public long getTickDelay() {
            return tickDelay;
        }
    }

    class MockHarvester extends Harvester {
        private boolean disable;
        private long executeCount = 0;

        @Override
        protected void execute() {
            executeCount++;
        }

        public long getExecuteCount() {
            return executeCount;
        }

        @Override
        public boolean isDisabled() {
            return disable;
        }

        public void setDisabled(boolean disable) {
            this.disable = disable;
        }
    }

    public Harvester createTestHarvester(String token, String host) {
        Harvester harvester = new Harvester();
        AgentConfiguration agentConfiguration = new AgentConfiguration();

        agentConfiguration.setApplicationToken(token);
        if (host != null)
            agentConfiguration.setCollectorHost(host);

        harvester.setAgentConfiguration(agentConfiguration);
        harvester.setHarvestConnection(new HarvestConnection());
        return harvester;
    }
}
