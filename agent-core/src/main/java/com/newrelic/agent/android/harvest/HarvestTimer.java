/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.stats.TicToc;
import com.newrelic.agent.android.util.NamedThreadFactory;
import com.newrelic.agent.android.harvest.AgentHealth;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class HarvestTimer implements Runnable {
    public final static long DEFAULT_HARVEST_PERIOD = TimeUnit.SECONDS.toMillis(60);
    private final static long HARVEST_PERIOD_LEEWAY = TimeUnit.SECONDS.toMillis(1);
    private final static long SESSION_REPLAY_SESSION_PERIOD = TimeUnit.HOURS.toMillis(4);
    private final static long NEVER_TICKED = -1;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Harvester"));
    private final AgentLog log = AgentLogManager.getAgentLog();
    private ScheduledFuture tickFuture = null;
    protected long period = DEFAULT_HARVEST_PERIOD;
    protected final Harvester harvester;
    protected long lastTickTime;
    private long startTimeMs;
    private long sessionReplayStartTimeMs;
    private final Lock lock = new ReentrantLock();
    private final AgentConfiguration agentConfiguration;

    public HarvestTimer(Harvester harvester, AgentConfiguration agentConfiguration) {
        this.harvester = harvester;
        this.agentConfiguration = agentConfiguration;
        this.startTimeMs = 0;
        this.sessionReplayStartTimeMs = 0;
    }

    public void run() {
        try {
            lock.lock();
            tickIfReady();
        } catch (Exception e) {
            log.error("HarvestTimer: Exception in timer tick: " + e.getMessage());
            AgentHealth.noticeException(e);
        } finally {
            lock.unlock();
        }
    }

    private void tickIfReady() {
        long lastTickDelta = timeSinceLastTick();

        // We want to run if the last tick was 5999ms ago (with a period of 6000ms) so we add a small leeway to the
        // delta before checking it against period.
        if (lastTickDelta + HARVEST_PERIOD_LEEWAY < period && lastTickDelta != NEVER_TICKED) {
            log.debug("HarvestTimer: Tick is too soon (" + lastTickDelta + " delta) Last tick time: " + lastTickTime + " . Skipping.");
            return;
        }

        log.debug("HarvestTimer: time since last tick: " + lastTickDelta);

        // Perform the actual tick logic
        try {
            tick();
        } catch (Exception e) {
            log.error("HarvestTimer: Exception in timer tick: " + e.getMessage());
            AgentHealth.noticeException(e);
        }

        log.debug("Set last tick time to: " + lastTickTime);
    }

    protected void tick() {
        log.debug("Harvest: tick");
        TicToc t = new TicToc().tic();

        try {

            if (FeatureFlag.featureEnabled(FeatureFlag.BackgroundReporting)) {
                harvester.execute();
                log.debug("Harvest: executed");
                log.debug("Harvest: executed in the background");
            } else {
                if (ApplicationStateMonitor.isAppInBackground()) {
                    log.error("HarvestTimer: Attempting to harvest while app is in background");
                } else {
                    harvester.execute();
                    log.debug("Harvest: executed");
                }
            }
            lastTickTime = now();
        } catch (Exception e) {
            log.error("HarvestTimer: Exception in harvest execute: " + e.getMessage());
            AgentHealth.noticeException(e);
        }

        // If the Harvester has become disabled, the HarvestTimer must be stopped.
        if (harvester.isDisabled()) {
            stop();
        }

        log.debug("HarvestTimer tick took " + t.toc() + "ms");

        // check session 4-hour
        checkAndResetSessionIfExpired();
    }

    public void start() {
        if (!FeatureFlag.featureEnabled(FeatureFlag.BackgroundReporting)) {
            if (ApplicationStateMonitor.isAppInBackground()) {
                log.warn("HarvestTimer: Attempting to start while app is in background");
                return;
            }
        }

        if (isRunning()) {
            log.warn("HarvestTimer: Attempting to start while already running");
            return;
        }

        if (period <= 0) {
            log.error("HarvestTimer: Refusing to start with a period of 0 ms");
            return;
        }

        log.debug("HarvestTimer: Starting with a period of " + period + "ms");
        startTimeMs = now();
        sessionReplayStartTimeMs = now();


        // Harvest timer MUST always start immediately, per the spec
        tickFuture = scheduler.scheduleWithFixedDelay(this, 0, period, TimeUnit.MILLISECONDS);

        // Advance the harvester now.
        harvester.start();
    }

    public void stop() {
        if (!isRunning()) {
            log.warn("HarvestTimer: Attempting to stop when not running");
            return;
        }
        cancelPendingTasks();
        log.debug("HarvestTimer: Stopped");
        startTimeMs = 0;
        sessionReplayStartTimeMs = 0;
        harvester.stop();
    }

    public void shutdown() {
        cancelPendingTasks();
        scheduler.shutdownNow();
    }

    /**
     * Executes a run of the Harvester immediately, disregarding any 'time since last tick' limits.
     * Does not affect the next scheduled harvest time, so if abused could result in over-harvesting.
     *
     * @param bWait If true, wait for harvest completion
     */
    public void tickNow(boolean bWait) {
        try {
            // TODO throttle on api abuse
            final HarvestTimer timer = this;
            ScheduledFuture<?> future = scheduler.schedule(() -> timer.tick(), 0, TimeUnit.MILLISECONDS);
            if (bWait && !future.isCancelled()) {
                future.get();
                startTimeMs = now();
            }

        } catch (Exception e) {
            log.error("Exception waiting for tickNow to finish: " + e.getMessage());
            AgentHealth.noticeException(e);
        }
    }

    public boolean isRunning() {
        return tickFuture != null;
    }

    public void setPeriod(long period) {
        this.period = period;
    }

    public long timeSinceLastTick() {
        if (lastTickTime == 0) {
            return NEVER_TICKED;
        }
        return now() - lastTickTime;
    }

    public long timeSinceStart() {
        if (startTimeMs == 0) {
            return 0;
        }
        return now() - startTimeMs;
    }

    public long sessionReplayTimeSinceStart() {
        if (sessionReplayStartTimeMs == 0) {
            return 0;
        }
        return now() - sessionReplayStartTimeMs;
    }

    private long now() {
        return System.currentTimeMillis();
    }

    protected void cancelPendingTasks() {
        try {
            lock.lock();
            if (tickFuture != null) {
                tickFuture.cancel(false);
                tickFuture = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void updateConfiguration(HarvestConfiguration harvestConfiguration) {
        // setPeriod(TimeUnit.MILLISECONDS.convert(harvestConfiguration.getData_report_period(), TimeUnit.SECONDS));
    }

    /**
     * Automatic session termination for the New Relic agent after 4 hours of continuous session time.
     */
    public void checkAndResetSessionIfExpired() {
        if (sessionReplayTimeSinceStart() >= SESSION_REPLAY_SESSION_PERIOD) {
            log.debug("HarvestTimer: Session replay limit reached (4 hours). Resetting session start time.");
            sessionReplayStartTimeMs = now();

            // start a new session
            log.debug("HarvestTimer: Session replay limit reached (4 hours). Creating new session.");
            final AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
            controller.getAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE)
                    .setStringValue(agentConfiguration.provideSessionId())
                    .setPersistent(false);
            // remove session duration attribute
            controller.removeAttribute(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE);

            // finalize session and send supportability metrics
            Harvest instance = new Harvest();
            instance.finalizeSession();
        }
    }
}
