/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.stats.TicToc;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class HarvestTimer implements Runnable, HarvestConfigurable {
    public final static long DEFAULT_HARVEST_PERIOD = 60 * 1000; // ms
    private final static long HARVEST_PERIOD_LEEWAY = 1000; // ms
    private final static long NEVER_TICKED = -1;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Harvester"));
    private final AgentLog log = AgentLogManager.getAgentLog();
    private ScheduledFuture tickFuture = null;
    protected long period = DEFAULT_HARVEST_PERIOD;
    protected final Harvester harvester;
    protected long lastTickTime;
    private long startTimeMs;
    private Lock lock = new ReentrantLock();

    public HarvestTimer(Harvester harvester) {
        this.harvester = harvester;
        this.startTimeMs = 0;
    }

    public void run() {
        try {
            lock.lock();
            tickIfReady();
        } catch (Exception e) {
            log.error("HarvestTimer: Exception in timer tick: " + e.getMessage());
            e.printStackTrace();
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
        long tickStart = now();

        // Perform the actual tick logic
        try {
            tick();
        } catch (Exception e) {
            log.error("HarvestTimer: Exception in timer tick: " + e.getMessage());
            e.printStackTrace();
            AgentHealth.noticeException(e);
        }

        lastTickTime = tickStart;
        log.debug("Set last tick time to: " + lastTickTime);
    }

    protected void tick() {
        log.debug("Harvest: tick");
        TicToc t = new TicToc();
        t.tic();

        try {
            if (FeatureFlag.featureEnabled(FeatureFlag.BackgroundReporting)) {
                harvester.execute();
                log.debug("Harvest: executed in the background");
            } else {
                if (ApplicationStateMonitor.isAppInBackground()) {
                    log.error("HarvestTimer: Attempting to harvest while app is in background");
                } else {
                    harvester.execute();
                    log.debug("Harvest: executed");
                }
            }
        } catch (Exception e) {
            log.error("HarvestTimer: Exception in harvest execute: " + e.getMessage());
            e.printStackTrace();
            AgentHealth.noticeException(e);
        }

        // If the Harvester has become disabled, the HarvestTimer must be stopped.
        if (harvester.isDisabled()) {
            stop();
        }

        long tickDelta = t.toc();

        log.debug("HarvestTimer tick took " + tickDelta + "ms");
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
        startTimeMs = System.currentTimeMillis();

        // Harvest timer MUST always start immediately, per the spec
        tickFuture = scheduler.scheduleAtFixedRate(this, 0, period, TimeUnit.MILLISECONDS);

        // Advance the harvester now.
        harvester.start();
    }

    public void stop() {
        if (!isRunning()) {
            log.warn("HarvestTimer: Attempting to stop when not running");
            return;
        }
        cancelPendingTasks();
        log.debug("HarvestTimer: Stopped.");
        startTimeMs = 0;
        harvester.stop();
    }

    public void shutdown() {
        cancelPendingTasks();
        scheduler.shutdownNow();
    }

    // Runs a tick of the Harvester immediately, disregarding any 'time since last tick' limits.
    public void tickNow() {
        final HarvestTimer timer = this;
        ScheduledFuture future = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                timer.tick();
            }
        }, 0, TimeUnit.SECONDS);
        try {
            future.get();
        } catch (Exception e) {
            log.error("Exception waiting for tickNow to finish: " + e.getMessage());
            e.printStackTrace();
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
        if (lastTickTime == 0)
            return -1;
        return now() - lastTickTime;
    }

    public long timeSinceStart() {
        if (startTimeMs == 0)
            return 0;
        return now() - startTimeMs;
    }

    private long now() {
        return System.currentTimeMillis();
    }

    protected void cancelPendingTasks() {
        try {
            lock.lock();
            if (tickFuture != null) {
                tickFuture.cancel(true);
                tickFuture = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void updateConfiguration(HarvestConfiguration harvestConfiguration) {
        // setPeriod(TimeUnit.MILLISECONDS.convert(harvestConfiguration.getData_report_period(), TimeUnit.SECONDS));
    }
}
