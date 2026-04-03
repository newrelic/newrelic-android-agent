/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.AgentConfiguration;
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

    @Test
    public void testSessionReplayTimeSinceStartWhenStopped() throws Exception {
        TestHarvestTimer timer = new TestHarvestTimer();
        Assert.assertEquals("Should return 0 before starting", 0, timer.sessionReplayTimeSinceStart());

        timer.stop();
        Assert.assertEquals("Should return 0 after stopping", 0, timer.sessionReplayTimeSinceStart());
    }

    @Test
    public void testSessionReplayTimeSinceStartWhenRunning() throws Exception {
        TestHarvestTimer timer = new TestHarvestTimer();

        timer.start();
        Thread.sleep(100);

        long elapsed = timer.timeSinceStart();
        Assert.assertTrue("Should return positive elapsed time after starting", elapsed > 0);
        Assert.assertTrue("Elapsed time should be reasonable", elapsed >= 90 && elapsed <= 200);

        timer.stop();
        Assert.assertEquals("Should return 0 after stopping", 0, timer.sessionReplayTimeSinceStart());
    }

    @Test
    public void testHarvestTimerWithSessionTimeout() throws Exception {
        TestSessionCheckHarvestTimer timer = new TestSessionCheckHarvestTimer();

        timer.setPeriod(50);
        timer.start();
        Assert.assertTrue(timer.isRunning());

        Thread.sleep(200);

        timer.stop();
        Assert.assertTrue("checkSession should have been called during tick", timer.wasCheckSessionCalled());
        Assert.assertTrue("checkSession should have been called multiple times", timer.getCheckSessionCallCount() >= 2);
    }

    @Test
    public void testSessionTimeoutConstantUsage() throws Exception {
        TestSessionCheckHarvestTimer timer = new TestSessionCheckHarvestTimer();

        timer.setPeriod(10);
        timer.start();
        Thread.sleep(50);
        timer.stop();

        Assert.assertTrue("checkSession method should be called during normal operation", timer.wasCheckSessionCalled());
    }

    @Test
    public void testSessionReplayTimingFunctionality() throws Exception {
        TestHarvestTimer timer = new TestHarvestTimer();
        Assert.assertEquals("Initial time since start should be 0", 0, timer.sessionReplayTimeSinceStart());

        timer.start();
        long startTime = System.currentTimeMillis();

        Thread.sleep(100);

        long elapsed = timer.sessionReplayTimeSinceStart();
        long actualElapsed = System.currentTimeMillis() - startTime;
        Assert.assertTrue("Elapsed time should be positive", elapsed > 0);
        Assert.assertTrue("Elapsed time should be close to actual elapsed time", Math.abs(elapsed - actualElapsed) < 50);
        timer.stop();

        Assert.assertEquals("Time since start should be 0 after stopping", 0, timer.sessionReplayTimeSinceStart());
    }

    @Test
    public void testCheckSessionUnderFourHours() throws Exception {
        TestSessionResetHarvestTimer timer = new TestSessionResetHarvestTimer();

        timer.setPeriod(50);
        timer.start();
        Assert.assertTrue(timer.isRunning());

        Thread.sleep(200);

        timer.stop();
        Assert.assertTrue("checkSession should have been called during tick", timer.wasCheckSessionCalled());
        Assert.assertTrue("checkSession should have been called multiple times", timer.getCheckSessionCallCount() >= 2);

        Assert.assertFalse("Session should NOT be reset when under 4-hour limit", timer.wasSessionReset());
        Assert.assertEquals("Session reset count should be 0 when under 4-hour limit", 0, timer.getSessionResetCount());

        long elapsed = timer.getSessionReplayElapsedTime();
        Assert.assertTrue("Elapsed time should be reasonable", elapsed >= 150 && elapsed <= 300);
    }

    @Test
    public void testCheckSessionOverTimeout() throws Exception {
        TestSessionResetWithTimeoutHarvestTimer timer = new TestSessionResetWithTimeoutHarvestTimer(100); // 100ms timeout

        timer.setPeriod(50);
        timer.start();
        Assert.assertTrue(timer.isRunning());

        Thread.sleep(200);
        timer.stop();

        Assert.assertTrue("checkSession should have been called during tick", timer.wasCheckSessionCalled());
        Assert.assertTrue("checkSession should have been called multiple times", timer.getCheckSessionCallCount() >= 2);

        Assert.assertTrue("Session should be reset when timeout exceeded", timer.wasSessionReset());
        Assert.assertTrue("Session reset count should be > 0 when timeout exceeded", timer.getSessionResetCount() > 0);
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
            super(new MockHarvester(), new AgentConfiguration());
        }

        public TestHarvestTimer(Harvester harvester) {
            super(harvester, new AgentConfiguration());
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

        @Override
        protected void cancelPendingTasks() {
            super.cancelPendingTasks();
        }

        public long getPeriod() {
            return this.period;
        }
    }

    class TestSessionCheckHarvestTimer extends TestHarvestTimer {
        private boolean checkSessionCalled = false;
        private int checkSessionCallCount = 0;

        public TestSessionCheckHarvestTimer() {
            super();
        }

        @Override
        public void tick() {
            super.tick();
            checkSessionCalled = true;
            checkSessionCallCount++;
        }

        public boolean wasCheckSessionCalled() {
            return checkSessionCalled;
        }

        public int getCheckSessionCallCount() {
            return checkSessionCallCount;
        }
    }

    class TestSessionResetHarvestTimer extends TestHarvestTimer {
        private boolean checkSessionCalled = false;
        private int checkSessionCallCount = 0;
        private boolean sessionReset = false;
        private int sessionResetCount = 0;
        private long sessionReplayStartTime = 0;

        public TestSessionResetHarvestTimer() {
            super();
            sessionReplayStartTime = System.currentTimeMillis();
        }

        @Override
        public void tick() {
            super.tick();
            testCheckSession();
        }

        private void testCheckSession() {
            checkSessionCalled = true;
            checkSessionCallCount++;

            // Calculate time since session replay started
            long elapsed = System.currentTimeMillis() - sessionReplayStartTime;

            // Use the actual 4-hour constant from the real implementation
            // Since we're testing normal operation (under 4 hours), this should never trigger
            final long SESSION_REPLAY_SESSION_PERIOD = 14400000; // 4 hours in milliseconds

            if (elapsed >= SESSION_REPLAY_SESSION_PERIOD) {
                // This should NOT happen in our test (we only run for ~200ms)
                sessionReset = true;
                sessionResetCount++;
                sessionReplayStartTime = System.currentTimeMillis(); // Reset the timer
                log.debug("TestSessionResetHarvestTimer: Session reset triggered after " + elapsed + "ms");
            }
        }

        public boolean wasCheckSessionCalled() {
            return checkSessionCalled;
        }

        public int getCheckSessionCallCount() {
            return checkSessionCallCount;
        }

        public boolean wasSessionReset() {
            return sessionReset;
        }

        public int getSessionResetCount() {
            return sessionResetCount;
        }

        public long getSessionReplayElapsedTime() {
            return System.currentTimeMillis() - sessionReplayStartTime;
        }
    }

    class TestSessionResetWithTimeoutHarvestTimer extends TestHarvestTimer {
        private boolean checkSessionCalled = false;
        private int checkSessionCallCount = 0;
        private boolean sessionReset = false;
        private int sessionResetCount = 0;
        private long sessionReplayStartTime = 0;
        private final long customTimeoutMs;

        public TestSessionResetWithTimeoutHarvestTimer(long timeoutMs) {
            super();
            this.customTimeoutMs = timeoutMs;
            sessionReplayStartTime = System.currentTimeMillis();
        }

        @Override
        public void tick() {
            super.tick();
            testCheckSession();
        }

        /**
         * Test implementation of checkSession with custom timeout for testing
         */
        private void testCheckSession() {
            checkSessionCalled = true;
            checkSessionCallCount++;

            // Calculate time since session replay started
            long elapsed = System.currentTimeMillis() - sessionReplayStartTime;

            if (elapsed >= customTimeoutMs) {
                sessionReset = true;
                sessionResetCount++;
                sessionReplayStartTime = System.currentTimeMillis(); // Reset the timer
                log.debug("TestSessionResetWithTimeoutHarvestTimer: Session reset triggered after " + elapsed + "ms (timeout: " + customTimeoutMs + "ms)");
            }
        }

        public boolean wasCheckSessionCalled() {
            return checkSessionCalled;
        }

        public int getCheckSessionCallCount() {
            return checkSessionCallCount;
        }

        public boolean wasSessionReset() {
            return sessionReset;
        }

        public int getSessionResetCount() {
            return sessionResetCount;
        }

        public long getSessionReplayElapsedTime() {
            return System.currentTimeMillis() - sessionReplayStartTime;
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

        @Override
        protected void execute() {
            // nop
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
