/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.activity.config.ActivityTraceConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfigurationDeserializer;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.tracing.ActivityTrace;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@code Harvester} is a state machine responsible for connecting to and posting data to the Collector.
 * <p/>
 * Agent configuration is specified with an {@link AgentConfiguration} object.
 * Communication with the Harvester is done via {@link HarvestConnection}.
 */
public class Harvester {
    private final AgentLog log = AgentLogManager.getAgentLog();

    /**
     * Valid states which Harvester can enter.
     * <ul>
     * <li>{@code UNINITIALIZED} - The Harvester has not yet initialized.</li>
     * <li>{@code DISCONNECTED} - The Harvester has initialized but is not connected.</li>
     * <li>{@code CONNECTED} - The Harvester has connected successfully to the Collector and is posting data</li>
     * <li>{@code DISABLED} - The Harvester is disabled.</li>
     * </ul>
     */
    protected enum State {
        UNINITIALIZED,
        DISCONNECTED,
        CONNECTED,
        DISABLED
    }

    // Tracks the current state of the Harvester
    private State state = State.UNINITIALIZED;

    /**
     * A bit which tracks whether the Harvester state has changed in the current cycle. This is to prevent
     * multiple transitions from occurring in one cycle. This has {@code protected} visibility for testing.
     */
    protected boolean stateChanged;

    private AgentConfiguration agentConfiguration;
    private HarvestConnection harvestConnection;
    private HarvestConfiguration harvestConfiguration = HarvestConfiguration.getDefaultHarvestConfiguration();
    private HarvestData harvestData;

    private final Collection<HarvestLifecycleAware> harvestListeners = new ArrayList<>() {{
        add(new HarvestAdapter() {
            @Override
            public void onHarvestConfigurationChanged() {
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_CONFIGURATION_CHANGED);
                AnalyticsControllerImpl.getInstance().recordBreadcrumb("FIXME harvestConfiguration", new HashMap<>() {{
                    put("changed", true);
                }});
            }
        });
    }};

    public void start() {
        fireOnHarvestStart();
    }

    public void stop() {
        fireOnHarvestStop();
    }

    /**
     * This method is executed when Harvester is in the {@link State#UNINITIALIZED} state.
     * <p>
     * Initialization should be performed in this state.
     */
    protected void uninitialized() {
        if (agentConfiguration == null) {
            log.error("Agent configuration unavailable.");
            return;
        }

        if (Agent.getImpl().updateSavedConnectInformation()) {
            configureHarvester(HarvestConfiguration.getDefaultHarvestConfiguration()); // clear stored harvester configuration
            harvestData.getDataToken().clear(); // invalidate dataToken to force reconnect
        }

        // Perform initialization tasks.
        Harvest.setHarvestConnectInformation(new ConnectInformation(Agent.getApplicationInformation(), Agent.getDeviceInformation()));

        harvestConnection.setApplicationToken(agentConfiguration.getApplicationToken());
        harvestConnection.setCollectorHost(agentConfiguration.getCollectorHost());
        harvestConnection.useSsl(agentConfiguration.useSsl());

        transition(State.DISCONNECTED);
        execute();
    }

    /**
     * This method is executed when Harvester is in the {@link State#DISCONNECTED} state.
     * <p>
     * This state attempts to connect to the collector and handles {@code connect} error conditions.
     */
    protected void disconnected() {
        if (null == harvestConfiguration) {
            configureHarvester(HarvestConfiguration.getDefaultHarvestConfiguration());
        }

        // If we've successfully loaded saved state, transition to CONNECTED state.
        if (harvestData.isValid()) {
            log.verbose("Skipping connect call, saved state is available: " + harvestData.getDataToken());
            // Record a session start metric.  Note that we only do this when not connecting.  The collector will record
            // the metric on a connect request.
            StatsEngine.get().sample(MetricNames.SESSION_START, 1);
            fireOnHarvestConnected(); // only fired once on edge transition
            transition(State.CONNECTED);
            execute();
            return;
        }

        log.info("Connecting, saved state is not available: " + harvestData.getDataToken());

        // Connect to the collector.
        HarvestResponse response = harvestConnection.sendConnect();

        // If response is null, there was some failure such as a network issue.
        if (response == null) {
            log.error("Unable to connect to the Collector.");
            return;
        }

        // If the response was okay, read the configuration.
        if (response.isOK()) {
            HarvestConfiguration configuration = parseHarvesterConfiguration(response);
            if (configuration == null) {
                log.error("Unable to configure Harvester using Collector configuration.");
                return;
            }

            configureHarvester(configuration);
            StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_COLLECTOR + "Harvest", response.getResponseTime());
            fireOnHarvestConnected();

            // Successfully connected!
            transition(State.CONNECTED);
            execute();

            if (!this.harvestConfiguration.equals(configuration)) {
                fireOnHarvestConfigurationChanged();    // notify listeners their configs may have changed
            }

            return;
        }

        log.debug("Harvest connect response: " + response.getResponseCode());

        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_COLLECTOR + "Harvest/Connect/Error/" + response.getResponseCode());

        switch (response.getResponseCode()) {
            case UNAUTHORIZED:
            case INVALID_AGENT_ID:
                // Unauthorized or invalid. Stay disconnected. Notify listeners we've been actively disconnected.
                // Notify all listeners that the harvester disconnected.
                harvestData.getDataToken().clear();
                fireOnHarvestDisconnected();
                return;

            case FORBIDDEN:
                if (response.isDisableCommand()) {
                    log.error("Collector has commanded Agent to disable.");
                    fireOnHarvestDisabled();
                    transition(State.DISABLED);
                    return;
                }
                log.error("Unexpected Collector response: FORBIDDEN");
                break;

            case UNSUPPORTED_MEDIA_TYPE:
            case ENTITY_TOO_LARGE:
                log.error("Invalid ConnectionInformation was sent to the Collector.");
                break;

            case REQUEST_TIMEOUT:
                log.warn("Harvest request has timed-out, and will retry during next harvest cycle.");
                break;

            case TOO_MANY_REQUESTS:
                log.warn("Harvest request has been throttled, and will retry during next harvest cycle.");
                break;

            case CONFIGURATION_UPDATE:      // should never get this on a connect
            default:
                log.error("An unknown error occurred when connecting to the Collector.");
        }

        fireOnHarvestError();

        // Stay in DISCONNECTED state
    }

    /**
     * This method is executed when Harvester is in the {@link State#CONNECTED} state.
     * <p>
     * This state performs {@code data} posts to the collector.
     */
    protected void connected() {
        if (!harvestData.isValid()) {
            log.error("Harvester: invalid data token! Agent must reconnect prior to upload.");
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_INVALID_DATA_TOKEN);
            harvestData.getDataToken().clear();
            fireOnHarvestSendFailed();
            transition(State.DISCONNECTED);
            return;
        }

        log.info("Harvester: connected");
        log.info("Harvester: Sending [" + harvestData.getHttpTransactions().count() + "] HTTP transactions.");
        log.info("Harvester: Sending [" + harvestData.getActivityTraces().count() + "] activity traces.");
        log.info("Harvester: Sending [" + harvestData.getSessionAttributes().size() + "] session attributes.");
        log.info("Harvester: Sending [" + harvestData.getAnalyticsEvents().size() + "] analytics events.");

        HarvestResponse response = harvestConnection.sendData(harvestData);

        // Network level error, or something else really bad. Don't clear the harvest data, we'll attempt again
        if (response == null || response.isUnknown()) {
            log.debug("Harvest data response: " + response.getResponseCode());
            checkOfflineAndPersist();
            fireOnHarvestSendFailed();
            return;
        }

        StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_COLLECTOR + "Harvest", response.getResponseTime());

        log.debug("Harvest data response: " + response.getResponseCode());
        log.debug("Harvest data response status code: " + response.getStatusCode());
        log.audit("Harvest data response BODY: " + response.getResponseBody());

        if (response.isError()) {
            fireOnHarvestError();

            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_COLLECTOR + "Harvest/Error/" + response.getResponseCode());

            switch (response.getResponseCode()) {
                case UNAUTHORIZED:
                case INVALID_AGENT_ID:
                    harvestData.reset();
                    harvestData.getDataToken().clear();
                    transition(State.DISCONNECTED);
                    break;

                case FORBIDDEN:
                    harvestData.reset();
                    if (response.isDisableCommand()) {
                        log.error("Collector has commanded Agent to disable.");
                        transition(State.DISABLED);
                        break;
                    }
                    log.error("Unexpected Collector response: FORBIDDEN");
                    transition(State.DISCONNECTED);
                    break;

                case UNSUPPORTED_MEDIA_TYPE:
                case ENTITY_TOO_LARGE:
                    harvestData.reset();
                    log.error("An invalid harvest payload was sent to the Collector.");
                    break;

                // In the following error cases, harvest data is retained, but may still
                // be expired using provided TTL values
                case REQUEST_TIMEOUT:
                    log.warn("Harvest request has timed-out, and will retry during next harvest cycle.");
                    break;

                case CONFIGURATION_UPDATE:
                    log.info("Harvest configuration has changed, and will be updated during next harvest cycle.");
                    harvestData.getDataToken().clear();     // invalidate dataToken to force reconnect
                    transition(State.DISCONNECTED);         // will force a reconnect on next harvest
                    break;

                case TOO_MANY_REQUESTS:
                    log.warn("Harvest request has been throttled, and will retry during next harvest cycle.");
                    break;

                default:
                    log.error("An unknown error occurred when connecting to the Collector.");
                    break;
            }

            //Offline Storage
            if (response.isNetworkError()) {
                checkOfflineAndPersist();
            }

            return;
        } else {
            //Offline Storage
            try {
                if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
                    Map<String, String> harvestDataObjects = Agent.getAllOfflineData();
                    for (Map.Entry<String, String> entry : harvestDataObjects.entrySet()) {
                        HarvestResponse eachResponse = harvestConnection.sendData(entry.getValue());
                        if (eachResponse.isOK()) {
                            File file = new File(entry.getKey());
                            file.delete();
                        }
                        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_COLLECTOR + "Harvest/OfflineStorage" + eachResponse.getResponseCode());
                    }
                }
            } catch (Exception ex) {
                log.error("OfflineStorage: " + ex);
            }
        }

        // Notify all listeners that the harvester finished
        fireOnHarvestComplete();

        harvestData.reset();
    }

    /**
     * This method is executed when Harvester is in the {@link State#DISABLED} state.
     * <p/>
     * This state performs a controlled shutdown of the agent.
     */
    protected void disabled() {
        Harvest.stop();
        fireOnHarvestDisabled();
    }

    /**
     * Runs one cycle of the state machine. Invokes the method which corresponds to the current state.
     *
     * @throws IllegalStateException if an illegal state is entered.
     */
    protected void execute() {
        log.debug("Harvester state: " + state);

        stateChanged = false;

        try {
            expireHarvestData();

            switch (state) {
                case UNINITIALIZED:
                    uninitialized();
                    break;
                case DISCONNECTED:
                    fireOnHarvestBefore();
                    disconnected();
                    break;
                case CONNECTED:
                    TaskQueue.synchronousDequeue();
                    fireOnHarvestBefore();
                    fireOnHarvest();
                    fireOnHarvestFinalize();
                    connected();
                    break;
                case DISABLED:
                    disabled();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } catch (Exception e) {
            log.error("Exception encountered while attempting to harvest", e);
            AgentHealth.noticeException(e);
        }
    }


    /**
     * Performs a controlled transition from the current state to a new state.
     * <p/>
     * Only certain state transitions are valid.
     * <p/>
     * <ol>
     * <li>The identity transition, which is a transition to the state which is already active, is allowed.</li>
     * <li>{@link State#DISABLED} may be entered from any state.</li>
     * <li>{@link State#UNINITIALIZED} may enter {@link State#DISCONNECTED} or {@link State#DISABLED}.</li>
     * <li>{@link State#DISCONNECTED} may enter {@link State#UNINITIALIZED}, {@link State#CONNECTED} or {@link State#DISABLED}.</li>
     * <li>{@link State#CONNECTED} may enter {@link State#DISCONNECTED} or {@link State#DISABLED}.</li>
     * <li>{@link State#DISABLED} may only re-enter {@link State#DISABLED}.</li>
     * </ol>
     *
     * @param newState The new state requested.
     */
    protected void transition(State newState) {

        // State may be changed only once per cycle.
        if (stateChanged) {
            log.debug("Ignoring multiple transition: " + newState);
            return;
        }

        // Identity transitions are always allowed.
        if (state == newState) {
            return;
        }

        switch (state) {
            case UNINITIALIZED:
                if (stateIn(newState, State.DISCONNECTED, newState, State.CONNECTED, State.DISABLED)) {
                    break;
                }
                throw new IllegalStateException();
            case DISCONNECTED:
                if (stateIn(newState, State.UNINITIALIZED, State.CONNECTED, State.DISABLED)) {
                    break;
                }
                throw new IllegalStateException();
            case CONNECTED:
                if (stateIn(newState, State.DISCONNECTED, State.DISABLED)) {
                    break;
                }
                throw new IllegalStateException();
            case DISABLED:
            default:
                throw new IllegalStateException();
        }
        changeState(newState);
    }

    /**
     * Transform a {@link HarvestResponse} into a {@link HarvestConfiguration}
     *
     * @param response HarvestResponse from the collector.
     * @return a HarvestConfiguration deserialized from the HarvestResponse.
     */
    HarvestConfiguration parseHarvesterConfiguration(HarvestResponse response) {
        HarvestConfiguration config = null;
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(ActivityTraceConfiguration.class, new ActivityTraceConfigurationDeserializer())
                    .create();
            config = gson.fromJson(response.getResponseBody(), HarvestConfiguration.class);
        } catch (JsonSyntaxException e) {
            log.error("Unable to parse collector configuration: " + e.getMessage());
            AgentHealth.noticeException(e);
        }
        return config;
    }

    private void configureHarvester(final HarvestConfiguration harvestConfiguration) {
        this.harvestConfiguration.reconfigure(harvestConfiguration);
        agentConfiguration.reconfigure(harvestConfiguration);
        harvestData.setDataToken(this.harvestConfiguration.getDataToken());
        Harvest.setHarvestConfiguration(this.harvestConfiguration);
    }

    // Change states and mark that the state has been changed.
    private void changeState(State newState) {
        log.debug("Harvester changing state: " + state + " -> " + newState);

        if (state == State.CONNECTED) {
            if (newState == State.DISCONNECTED) {
                fireOnHarvestDisconnected();
            } else if (newState == State.DISABLED) {
                fireOnHarvestDisabled();
            }
        }

        state = newState;
        stateChanged = true;
    }

    // Test whether a state is in a set of states.
    private boolean stateIn(State testState, State... legalStates) {
        for (State state : legalStates) {
            if (testState == state) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the current Harvester {@link Harvester.State}
     *
     * @return The current Harvester {@link Harvester.State}
     */
    public State getCurrentState() {
        return state;
    }

    /**
     * Check whether the Harvester is disabled.
     *
     * @return true if Harvester state is {@link State#DISABLED}
     */
    public boolean isDisabled() {
        return State.DISABLED == state;
    }

    public void addHarvestListener(HarvestLifecycleAware harvestAware) {
        if (harvestAware == null) {
            log.error("Can't add null harvest listener");
            new Exception().printStackTrace();
            return;
        }

        synchronized (harvestListeners) {
            if (harvestListeners.contains(harvestAware)) {
                return;
            }
            harvestListeners.add(harvestAware);
        }
    }

    public void removeHarvestListener(HarvestLifecycleAware harvestAware) {
        synchronized (harvestListeners) {
            if (!harvestListeners.contains(harvestAware)) {
                return;
            }
            harvestListeners.remove(harvestAware);
        }
    }

    public void expireHarvestData() {
        if (harvestData != null) {
            // FIXME - never expire data when network unreachable
            expireHttpTransactions();
            expireActivityTraces();
            expireAnalyticsEvents();
        }
    }

    public void expireHttpTransactions() {
        HttpTransactions transactions = harvestData.getHttpTransactions();

        // The synchronized instance is not actually local.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (transactions) {
            Collection<HttpTransaction> expiredTransactions = new ArrayList<HttpTransaction>();
            long now = System.currentTimeMillis();
            long maxAge = harvestConfiguration.getReportMaxTransactionAgeMilliseconds();

            // Find all HttpTransactions which should be expired.
            for (HttpTransaction txn : transactions.getHttpTransactions()) {
                if (txn.getTimestamp() < now - maxAge) {
                    log.audit("HttpTransaction too old, purging: " + txn);
                    expiredTransactions.add(txn);
                }
            }

            if (!expiredTransactions.isEmpty()) {
                log.debug("Purging [" + expiredTransactions.size() + "] expired HttpTransactions from HarvestData");
                // Remove all of the expired HttpTransactions.
                for (HttpTransaction txn : expiredTransactions) {
                    transactions.remove(txn);
                }
            }
        }
    }

    public void expireActivityTraces() {
        ActivityTraces traces = harvestData.getActivityTraces();

        // The synchronized instance is not actually local.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (traces) {
            Collection<ActivityTrace> expiredTraces = new ArrayList<ActivityTrace>();
            long maxAttempts = harvestConfiguration.getActivity_trace_max_report_attempts();

            // Find all ActivityTraces which should be expired.
            for (ActivityTrace trace : traces.getActivityTraces()) {
                if (trace.getReportAttemptCount() >= maxAttempts) {
                    log.audit("ActivityTrace has had " + trace.getReportAttemptCount() +
                            " report attempts, purging: " + trace);
                    expiredTraces.add(trace);
                }
            }

            // Removed all of the expired ActivityTraces
            if (!expiredTraces.isEmpty()) {
                log.debug("Purging [" + expiredTraces.size() + "] expired ActivityTraces from HarvestData");
                for (ActivityTrace trace : expiredTraces) {
                    traces.remove(trace);
                }
            }
        }
    }

    public void expireAnalyticsEvents() {
        // we don't expire analytics, could be prematurely dropped before event harvest lifecycle completes.
    }

    public void setAgentConfiguration(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    AgentConfiguration getAgentConfiguration() {
        return this.agentConfiguration;
    }

    public void setHarvestConnection(HarvestConnection connection) {
        this.harvestConnection = connection;
    }

    public HarvestConnection getHarvestConnection() {
        return harvestConnection;
    }

    public void setHarvestData(HarvestData harvestData) {
        this.harvestData = harvestData;
    }

    public HarvestData getHarvestData() {
        return harvestData;
    }

    private void fireOnHarvestBefore() {
        // Notify all listeners that an execute cycle is about to occur.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestBefore();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestBefore", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestStart() {
        // Notify all listeners that an execute cycle has begun.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestStart();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestStart", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestStop() {
        // Notify all listeners that the harvester has shut down.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestStop();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestStop", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvest() {
        // Notify all listeners that an execute cycle is occurring.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvest();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvest", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestFinalize() {
        // Notify all listeners that an execute cycle is completing.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestFinalize();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestFinalize", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestDisabled() {
        // Notify all listeners that the harvester disabled.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestDisabled();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestDisabled", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestDisconnected() {
        // Notify all listeners that the harvester disabled.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestDisconnected();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestDisconnected", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestError() {
        // Notify all listeners that an error occurred.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestError();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestError", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestSendFailed() {
        // Notify all listeners that no response was received from harvest send
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestSendFailed();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestSendFailed", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestComplete() {
        // Notify all listeners that the harvest completed
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestComplete();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestComplete", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestConnected() {
        // Notify all listeners that the harvester connected.
        try {
            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestConnected();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestConnected", e);
            AgentHealth.noticeException(e);
        }
    }

    private void fireOnHarvestConfigurationChanged() {
        // Notify all listeners that the harvester connected.
        try {
            // Invalidate the data token, which then forces a reconnect on next harvest
            harvestData.getDataToken().clear();

            for (HarvestLifecycleAware harvestAware : getHarvestListeners()) {
                harvestAware.onHarvestConfigurationChanged();
            }
        } catch (Exception e) {
            log.error("Error in fireOnHarvestConfigurationChanged", e);
            AgentHealth.noticeException(e);
        }
    }

    public void checkOfflineAndPersist() {
        try {
            if (!FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
                return;
            }

            //Offline Storage
            if (harvestData != null && harvestData.toString().length() > 0) {
                Agent.persistHarvestDataToDisk(harvestData.toJsonString());
                harvestData.reset();
                log.info("Harvest data was stored to disk due to network errors, will resubmit in next cycle when network is available.");
            } else {
                log.info("No harvest data was stored during this cycle");
            }
        } catch (Exception ex) {
            log.error("Error in persisting data: ", ex);
        }
    }

    public void setConfiguration(HarvestConfiguration configuration) {
        this.harvestConfiguration = configuration;
    }

    public void setHarvestConfiguration(HarvestConfiguration harvestConfiguration) {
        this.harvestConfiguration = harvestConfiguration;
    }

    HarvestConfiguration getHarvestConfiguration() {
        return harvestConfiguration;
    }

    Collection<HarvestLifecycleAware> getHarvestListeners() {
        return new ArrayList<>(harvestListeners);
    }

}
