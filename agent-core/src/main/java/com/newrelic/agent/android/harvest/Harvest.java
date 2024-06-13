/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.SessionEvent;
import com.newrelic.agent.android.harvest.type.Harvestable;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.tracing.ActivityTrace;

import java.util.ArrayList;
import java.util.Collection;

public class Harvest implements HarvestConfigurable {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final boolean DISABLE_ACTIVITY_TRACE_LIMITS_FOR_DEBUGGING = false;
    public static final long INVALID_SESSION_DURATION = 0;

    protected static Harvest instance = new Harvest();

    private Harvester harvester;
    private HarvestConnection harvestConnection;
    private HarvestTimer harvestTimer;
    protected HarvestData harvestData;
    private HarvestDataValidator harvestDataValidator;

    // A list of entities that want to listen to lifecycle events. Used for pre-initialization.
    private static final Collection<HarvestLifecycleAware> unregisteredLifecycleListeners = new ArrayList<HarvestLifecycleAware>();

    // Caches for incoming API calls during pre-initialization.
    private static final HarvestableCache activityTraceCache = new HarvestableCache();

    private HarvestConfiguration harvestConfiguration = HarvestConfiguration.getDefaultHarvestConfiguration();

    /* Static access methods */
    public static void initialize(AgentConfiguration agentConfiguration) {
        instance.initializeHarvester(agentConfiguration);
        registerUnregisteredListeners();
        addHarvestListener(StatsEngine.get());
    }

    public void initializeHarvester(AgentConfiguration agentConfiguration) {
        createHarvester();
        harvester.setAgentConfiguration(agentConfiguration);
        harvester.setHarvestConfiguration(instance.getConfiguration());
        flushHarvestableCaches();
    }

    public static void start() {
        if (instance.getHarvestTimer() != null) {
            instance.getHarvestTimer().start();
        } else {
            log.error("Harvest timer is null");
        }
    }

    public static void stop() {
        if (instance.getHarvestTimer() != null) {
            instance.getHarvestTimer().stop();
        } else {
            log.error("Harvest timer is null");
        }
    }

    public static void harvestNow(boolean finalizeSession, boolean bWait) {
        if (isInitialized()) {
            if (finalizeSession) {
                // add session meta to next harvest
                instance.finalizeSession();

                // flush eventManager buffer on next harvest
                AnalyticsControllerImpl.getInstance().getEventManager().setTransmitRequired();
            }

            final HarvestTimer harvestTimer = instance.getHarvestTimer();
            if (harvestTimer != null) {
                harvestTimer.tickNow(bWait);
            }
        }
    }

    /**
     * Instances are used without checking throughout the code.
     * Don't allow null instances through the settor
     */
    public static void setInstance(Harvest harvestInstance) {
        if (harvestInstance == null) {
            log.error("Attempt to set Harvest instance to null value!");
        } else {
            instance = harvestInstance;
        }
    }

    /* Instance methods */
    public void createHarvester() {
        harvestConnection = new HarvestConnection();
        harvestData = new HarvestData();
        harvester = new Harvester();
        harvester.setHarvestConnection(harvestConnection);
        harvester.setHarvestData(harvestData);
        harvestTimer = new HarvestTimer(harvester);
        harvestDataValidator = new HarvestDataValidator();
        Harvest.addHarvestListener(harvestDataValidator);
    }

    public void shutdownHarvester() {
        harvestTimer.shutdown();
        harvestTimer = null;
        harvester = null;
        harvestConnection = null;
        harvestData = null;
    }

    public static void shutdown() {
        if (isInitialized()) {
            stop();
            instance.shutdownHarvester();
        }
    }


    public static void addHttpTransaction(HttpTransaction txn) {
        if (isDisabled()) return;

        HttpTransactions transactions = instance.getHarvestData().getHttpTransactions();
        instance.getHarvester().expireHttpTransactions();

        int transactionLimit = instance.getConfiguration().getReport_max_transaction_count();
        if (transactions.count() >= transactionLimit) {
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_TRANS_DROPPED);
            log.debug("Maximum number of transactions (" + transactionLimit + ") reached. HTTP Transaction dropped.");
            return;
        }
        transactions.add(txn);

        AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();

        // record the transaction as a NetworkRequest event
        analyticsController.createNetworkRequestEvents(txn);
    }

    public static void addActivityTrace(ActivityTrace activityTrace) {
        if (isDisabled()) return;

        if (!isInitialized()) {
            activityTraceCache.add(activityTrace);
            return;
        }

        if (activityTrace.rootTrace == null) {
            log.error("Activity trace is lacking a root trace!");
            return;
        }

        if (activityTrace.rootTrace.childExclusiveTime == 0) {
            log.error("Total trace exclusive time is zero. Ignoring trace " + activityTrace.rootTrace.displayName);
            return;
        }

        double traceUtilization = (double) activityTrace.rootTrace.childExclusiveTime / (double) activityTrace.rootTrace.getDurationAsMilliseconds();
        boolean isBelowMinUtilization = traceUtilization < instance.getConfiguration().getActivity_trace_min_utilization();

        if (isBelowMinUtilization) {
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_TRACES_IGNORED);
            log.debug("Exclusive trace time is too low (" + activityTrace.rootTrace.childExclusiveTime + "/" + activityTrace.rootTrace.getDurationAsMilliseconds() + "). Ignoring trace " + activityTrace.rootTrace.displayName);
            return;
        }

        ActivityTraces activityTraces = instance.getHarvestData().getActivityTraces();
        ActivityTraceConfiguration configurations = instance.getActivityTraceConfiguration();

        instance.getHarvester().expireActivityTraces();

        if (DISABLE_ACTIVITY_TRACE_LIMITS_FOR_DEBUGGING) {
            log.warn("WARNING: Activity trace limits are being ignored!");
        } else {
            if (activityTraces.count() >= configurations.getMaxTotalTraceCount()) {
                log.debug("Activity trace limit of " + configurations.getMaxTotalTraceCount() + " exceeded. Ignoring trace: " + activityTrace.toJsonString());
                return;
            }
        }

        log.debug("Adding activity trace: " + activityTrace.toJsonString());
        activityTraces.add(activityTrace);
    }

    public static void addMetric(Metric metric) {
        if (isDisabled() || !isInitialized()) return;
        instance.getHarvestData().getMetrics().addMetric(metric);
    }

    public static void addAgentHealthException(AgentHealthException exception) {
        if (isDisabled() || !isInitialized()) return;

        instance.getHarvestData().getAgentHealth().addException(exception);
    }

    public static void addHarvestListener(HarvestLifecycleAware harvestAware) {
        if (harvestAware == null) {
            log.error("Harvest: Argument to addHarvestListener cannot be null.");
            return;
        }

        if (!isInitialized()) {
            if (!isUnregisteredListener(harvestAware)) {
                addUnregisteredListener(harvestAware);
            }
            return;
        }
        instance.getHarvester().addHarvestListener(harvestAware);
    }

    public static void removeHarvestListener(HarvestLifecycleAware harvestAware) {
        if (harvestAware == null) {
            log.error("Harvest: Argument to removeHarvestListener cannot be null.");
            return;
        }

        if (!isInitialized()) {
            if (isUnregisteredListener(harvestAware)) {
                removeUnregisteredListener(harvestAware);
            }
            return;
        }
        instance.getHarvester().removeHarvestListener(harvestAware);
    }

    public static boolean isInitialized() {
        return (instance != null) && (instance.getHarvester() != null);
    }

    // for testing
    public static int getActivityTraceCacheSize() {
        return activityTraceCache.getSize();
    }

    public static long getMillisSinceStart() {
        long lTime = INVALID_SESSION_DURATION;
        final Harvest harvest = getInstance();
        if (harvest != null) {
            if (harvest.getHarvestTimer() != null) {
                lTime = harvest.getHarvestTimer().timeSinceStart();

                // check for calculated underflow
                if (lTime < 0) {
                    lTime = INVALID_SESSION_DURATION;
                }
            }
        }

        return lTime;
    }

    public static boolean shouldCollectActivityTraces() {
        if (isDisabled())
            return false;

        // Collect ATs if we aren't yet initialized.
        if (!isInitialized())
            return true;

        // Collect ATs if we haven't been told otherwise by the collector.
        ActivityTraceConfiguration configurations = instance.getActivityTraceConfiguration();
        if (configurations == null)
            return true;

        return configurations.getMaxTotalTraceCount() > 0;
    }

    private void flushHarvestableCaches() {
        try {
            flushActivityTraceCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void flushActivityTraceCache() {
        Collection<Harvestable> activityTraces = activityTraceCache.flush();
        for (Harvestable activityTrace : activityTraces) {
            addActivityTrace((ActivityTrace) activityTrace);
        }
    }

    private static void addUnregisteredListener(HarvestLifecycleAware harvestAware) {
        if (harvestAware == null)
            return;

        synchronized (unregisteredLifecycleListeners) {
            unregisteredLifecycleListeners.add(harvestAware);
        }
    }

    private static void removeUnregisteredListener(HarvestLifecycleAware harvestAware) {
        if (harvestAware == null)
            return;

        synchronized (unregisteredLifecycleListeners) {
            unregisteredLifecycleListeners.remove(harvestAware);
        }
    }

    private static void registerUnregisteredListeners() {
        for (HarvestLifecycleAware harvestAware : unregisteredLifecycleListeners) {
            addHarvestListener(harvestAware);
        }
        unregisteredLifecycleListeners.clear();
    }

    private static boolean isUnregisteredListener(HarvestLifecycleAware harvestAware) {
        if (harvestAware == null)
            return false;
        return unregisteredLifecycleListeners.contains(harvestAware);
    }

    protected HarvestTimer getHarvestTimer() {
        return harvestTimer;
    }

    public static Harvest getInstance() {
        return instance;
    }

    protected Harvester getHarvester() {
        return harvester;
    }

    public HarvestData getHarvestData() {
        return harvestData;
    }

    public HarvestConfiguration getConfiguration() {
        return harvestConfiguration;
    }

    public HarvestConnection getHarvestConnection() {
        return harvestConnection;
    }

    public void setHarvestConnection(HarvestConnection connection) {
        harvestConnection = connection;
    }

    @Override
    public void setConfiguration(HarvestConfiguration harvestConfiguration) {
        updateConfiguration(harvestConfiguration);
    }

    @Override
    public void updateConfiguration(HarvestConfiguration newConfiguration) {
        harvestConfiguration.updateConfiguration(newConfiguration);
        harvestTimer.updateConfiguration(newConfiguration);
        harvestConnection.updateConfiguration(newConfiguration);
        harvestData.updateConfiguration(newConfiguration);
        harvester.updateConfiguration(newConfiguration);
    }

    public void setConnectInformation(ConnectInformation connectInformation) {
        harvestConnection.setConnectInformation(connectInformation);
        harvestData.setDeviceInformation(connectInformation.getDeviceInformation());
    }

    public static void setHarvestConfiguration(HarvestConfiguration configuration) {
        if (!isInitialized()) {
            log.error("Cannot configure Harvester before initialization.");
            return;
        }
        log.debug("Harvest Configuration: " + configuration);
        instance.setConfiguration(configuration);
    }

    public static HarvestConfiguration getHarvestConfiguration() {
        if (!isInitialized()) {
            return HarvestConfiguration.getDefaultHarvestConfiguration();
        }

        return instance.getConfiguration();
    }

    public static void setHarvestConnectInformation(ConnectInformation connectInformation) {
        if (!isInitialized()) {
            log.error("Cannot configure Harvester before initialization.");
            return;
        }
        log.debug(("Setting Harvest connect information: " + connectInformation));
        instance.setConnectInformation(connectInformation);
    }

    public static boolean isDisabled() {
        if (!isInitialized())
            return false;
        return instance.getHarvester().isDisabled();
    }

    protected ActivityTraceConfiguration getActivityTraceConfiguration() {
        return harvestConfiguration.getAt_capture();
    }

    void finalizeSession() {
        long sessionDuration = Harvest.getMillisSinceStart();

        if (sessionDuration == Harvest.INVALID_SESSION_DURATION) {
            log.error("Session duration is invalid!");
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_INVALID_DURATION);
        }

        float sessionDurationAsSeconds = (float) sessionDuration / 1000.0f;

        StatsEngine.get().sample(MetricNames.SESSION_DURATION, sessionDurationAsSeconds);

        log.debug("Harvest: Generating sessionDuration attribute with value " + sessionDurationAsSeconds);
        AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
        analyticsController.setAttribute(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE, sessionDurationAsSeconds, false);

        log.debug("Harvest: Generating session event.");
        SessionEvent sessionEvent = new SessionEvent();
        analyticsController.addEvent(sessionEvent);
    }
}
