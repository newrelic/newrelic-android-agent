/**
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.aei.ApplicationExitConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttributeStore;
import com.newrelic.agent.android.analytics.AnalyticsEventStore;
import com.newrelic.agent.android.crash.CrashStore;
import com.newrelic.agent.android.harvest.HarvestConfigurable;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.NullPayloadStore;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadStore;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentConfiguration implements HarvestConfigurable {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private static final String DEFAULT_COLLECTOR_HOST = "mobile-collector.newrelic.com";
    private static final String DEFAULT_CRASH_COLLECTOR_HOST = "mobile-crash.newrelic.com";

    public static final String DEFAULT_REGION_COLLECTOR_HOST = "mobile-collector.%s.nr-data.net";
    private static final String DEFAULT_REGION_CRASH_COLLECTOR_HOST = "mobile-crash.%s.nr-data.net";

    public static final String DEFAULT_FED_RAMP_COLLECTOR_HOST = "gov-mobile-collector.newrelic.com";
    private static final String DEFAULT_FED_RAMP_CRASH_COLLECTOR_HOST = "gov-mobile-crash.newrelic.com";

    private static final String HEX_COLLECTOR_PATH = "/mobile/f";
    private static final int HEX_COLLECTOR_TIMEOUT = 5000; // 5 seconds

    private static final int NUM_IO_THREADS = 3;    // Harvest + Crash + Flatbuffer
    private static final int PAYLOAD_TTL = 2 * 24 * 60 * 60 * 1000;    // 2 days in ms

    static final String DEFAULT_DEVICE_UUID = "0";
    static final int DEVICE_UUID_MAX_LEN = 40;

    protected static AtomicReference<AgentConfiguration> instance = new AtomicReference<>(new AgentConfiguration());

    private String collectorHost = DEFAULT_COLLECTOR_HOST;
    private String crashCollectorHost = DEFAULT_CRASH_COLLECTOR_HOST;
    private String applicationToken;
    private boolean useSsl = true;
    private boolean reportCrashes = false;
    private boolean reportHandledExceptions = true;
    private boolean enableAnalyticsEvents = true;
    private String sessionID = provideSessionId();
    private String customApplicationVersion = null;
    private String customBuildId = null;
    private String region = null;
    private String launchActivityClassName = null;
    private CrashStore crashStore;
    private AnalyticsAttributeStore analyticsAttributeStore;
    private PayloadStore<Payload> payloadStore = new NullPayloadStore<Payload>();
    private AnalyticsEventStore eventStore;
    private ApplicationFramework applicationFramework = ApplicationFramework.Native;
    private String applicationFrameworkVersion = Agent.getVersion();
    private String deviceID;
    private String entityGuid;

    // Support remote configuration for these features
    private LogReportingConfiguration logReportingConfiguration = new LogReportingConfiguration(false, LogLevel.INFO);
    private ApplicationExitConfiguration applicationExitConfiguration = new ApplicationExitConfiguration(true);

    public String getApplicationToken() {
        return applicationToken;
    }

    public void setApplicationToken(String applicationToken) {
        this.applicationToken = applicationToken;
        this.region = parseRegionFromApplicationToken(applicationToken);
        if (FeatureFlag.featureEnabled(FeatureFlag.FedRampEnabled)) {
            this.collectorHost = DEFAULT_FED_RAMP_COLLECTOR_HOST;
            this.crashCollectorHost = DEFAULT_FED_RAMP_CRASH_COLLECTOR_HOST;
        } else {
            if (this.region != null) {
                this.collectorHost = String.format(DEFAULT_REGION_COLLECTOR_HOST, region);
                this.crashCollectorHost = String.format(DEFAULT_REGION_CRASH_COLLECTOR_HOST, region);
            } else {
                this.collectorHost = DEFAULT_COLLECTOR_HOST;
                this.crashCollectorHost = DEFAULT_CRASH_COLLECTOR_HOST;
            }
        }
    }

    public String getCollectorHost() {
        return collectorHost;
    }

    public void setCollectorHost(String collectorHost) {
        this.collectorHost = collectorHost;
    }

    public String getCrashCollectorHost() {
        return crashCollectorHost;
    }

    public void setCrashCollectorHost(String crashCollectorHost) {
        this.crashCollectorHost = crashCollectorHost;
    }

    public boolean useSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        if (!useSsl) {
            log.error("Unencrypted http requests are no longer supported");
        }
        this.useSsl = true;
    }

    public boolean getReportCrashes() {
        return reportCrashes;
    }

    public void setReportCrashes(boolean reportCrashes) {
        this.reportCrashes = reportCrashes;
    }

    public CrashStore getCrashStore() {
        return crashStore;
    }

    public void setCrashStore(CrashStore crashStore) {
        this.crashStore = crashStore;
    }

    public AnalyticsEventStore getEventStore() {
        return eventStore;
    }

    public void setEventStore(AnalyticsEventStore eventStore) {
        this.eventStore = eventStore;
    }

    public boolean getReportHandledExceptions() {
        return reportHandledExceptions;
    }

    public void setReportHandledExceptions(boolean reportHandledExceptions) {
        this.reportHandledExceptions = reportHandledExceptions;
    }

    public AnalyticsAttributeStore getAnalyticsAttributeStore() {
        return analyticsAttributeStore;
    }

    public void setAnalyticsAttributeStore(AnalyticsAttributeStore analyticsAttributeStore) {
        this.analyticsAttributeStore = analyticsAttributeStore;
    }

    public boolean getEnableAnalyticsEvents() {
        return enableAnalyticsEvents;
    }

    public void setEnableAnalyticsEvents(boolean enableAnalyticsEvents) {
        this.enableAnalyticsEvents = enableAnalyticsEvents;
    }

    public String getSessionID() {
        return sessionID;
    }

    public String getCustomApplicationVersion() {
        return customApplicationVersion;
    }

    public void setCustomApplicationVersion(String customApplicationVersion) {
        this.customApplicationVersion = customApplicationVersion;
    }

    public String getCustomBuildIdentifier() {
        return customBuildId;
    }

    public void setCustomBuildIdentifier(String customBuildId) {
        this.customBuildId = customBuildId;
    }

    public ApplicationFramework getApplicationFramework() {
        return applicationFramework;
    }

    public void setApplicationFramework(ApplicationFramework ApplicationFramework) {
        this.applicationFramework = ApplicationFramework;
    }

    public String getApplicationFrameworkVersion() {
        return applicationFrameworkVersion;
    }

    public void setApplicationFrameworkVersion(String applicationFrameworkVersion) {
        this.applicationFrameworkVersion = applicationFrameworkVersion;
    }

    public String provideSessionId() {
        sessionID = UUID.randomUUID().toString();
        return sessionID;
    }

    public String getHexCollectorPath() {
        return HEX_COLLECTOR_PATH;
    }

    public String getHexCollectorHost() {
        return getCollectorHost();
    }

    public int getHexCollectorTimeout() {
        return HEX_COLLECTOR_TIMEOUT;
    }

    public String getAppTokenHeader() {
        return Constants.Network.APPLICATION_LICENSE_HEADER;
    }

    public String getAppVersionHeader() {
        return Constants.Network.APP_VERSION_HEADER;
    }

    public String getDeviceOsNameHeader() {
        return Constants.Network.DEVICE_OS_NAME_HEADER;
    }

    public int getIOThreadSize() {
        return NUM_IO_THREADS;      // generalize this to configuration
    }

    public PayloadStore<Payload> getPayloadStore() {
        return payloadStore;
    }

    public void setPayloadStore(PayloadStore<Payload> payloadStore) {
        this.payloadStore = payloadStore;
    }

    public int getPayloadTTL() {
        return PAYLOAD_TTL;
    }

    String getDefaultCollectorHost() {
        return DEFAULT_COLLECTOR_HOST;
    }

    String getDefaultCrashCollectorHost() {
        return DEFAULT_CRASH_COLLECTOR_HOST;
    }

    String getRegionalCollectorFromLicenseKey(String key) {
        final String region = parseRegionFromApplicationToken(key);

        if (!(region == null || "".equals(key))) {
            return String.format(DEFAULT_REGION_COLLECTOR_HOST, region);
        }

        return DEFAULT_COLLECTOR_HOST;
    }

    String getFedRampCollectorHost() {
        return DEFAULT_FED_RAMP_COLLECTOR_HOST;
    }

    String getFedRampCrashCollectorHost() {
        return DEFAULT_FED_RAMP_CRASH_COLLECTOR_HOST;
    }

    /**
     * The first six bytes of the Mobile license key MUST match the regex pattern ^(.+?x).
     * If the regex does not match, the hostname MUST default to mobile-collector.newrelic.com.
     * If the regex matches, agents MUST strip trailing x characters from the matched identifier and
     * insert the result between mobile-collector. and .newrelic.com.
     *
     * @param applicationToken
     * @return Normalized region specifier, or null if region not detected
     */
    String parseRegionFromApplicationToken(String applicationToken) {
        if (null == applicationToken || "".equals(applicationToken)) {
            return null;
        }

        // spec says: [a-z]{2,3}[0-9]{2}x{1,2}
        final Pattern pattern = Pattern.compile("^(.+?)x{1,2}.*");
        final Matcher matcher = pattern.matcher(applicationToken);

        if (matcher.matches()) {
            try {
                final String prefix = matcher.group(1);
                if (prefix == null || "".equals(prefix)) {
                    log.warn("Region prefix empty");
                } else {
                    return prefix;
                }

            } catch (Exception e) {
                log.error("getRegionalCollectorFromLicenseKey: " + e);
            }
        }

        return null;
    }

    /**
     * Allow caller to set deviceUUID
     * . NULL or empty string is valid but will be converted to {@link #DEFAULT_DEVICE_UUID}
     * . Whitespace will be removed
     * . Values longer than {@link #DEVICE_UUID_MAX_LEN} characters will be truncated
     *
     * @param deviceUUID
     */
    public void setDeviceID(String deviceUUID) {

        if (deviceUUID == null) {
            this.deviceID = DEFAULT_DEVICE_UUID;

        } else {
            deviceUUID = deviceUUID.trim();
            if (deviceUUID.isEmpty()) {
                this.deviceID = DEFAULT_DEVICE_UUID;
            } else {
                if (deviceUUID.length() > DEVICE_UUID_MAX_LEN) {
                    StatsEngine.get().inc(MetricNames.METRIC_UUID_TRUNCATED);
                }
                this.deviceID = deviceUUID.substring(0, Math.min(DEVICE_UUID_MAX_LEN, deviceUUID.length()));
            }
        }
    }

    /**
     * Returns device uuid set through start-up configuration.
     *
     * @return Configured device uuid
     */
    public String getDeviceID() {
        return deviceID;
    }

    public String getEntityGuid() {
        return entityGuid;
    }

    public void setEntityGuid(String entityGuid) {
        if (entityGuid != null && !entityGuid.isEmpty()) {
            this.entityGuid = entityGuid.trim().strip();
        }
    }

    public String getLaunchActivityClassName() {
        return launchActivityClassName;
    }

    public void setLaunchActivityClassName(String launchActivityClassName) {
        this.launchActivityClassName = launchActivityClassName;
    }

    public LogReportingConfiguration getLogReportingConfiguration() {
        return logReportingConfiguration;
    }

    public ApplicationExitConfiguration getApplicationExitConfiguration() {
        return applicationExitConfiguration;
    }

    /**
     * Update agent config with any changes returned in the harvest response.
     *
     * @param harvestConfiguration
     */
    @Override
    public void updateConfiguration(HarvestConfiguration harvestConfiguration) {
        // update the global agent config w/changes
        applicationExitConfiguration.setConfiguration(harvestConfiguration.getRemote_configuration().applicationExitConfiguration);
        logReportingConfiguration.setConfiguration(harvestConfiguration.getRemote_configuration().logReportingConfiguration);
        entityGuid = harvestConfiguration.getEntity_guid();

        if (instance.get() != null) {
            AgentConfiguration agentConfiguration = instance.get();
            if (agentConfiguration != null && agentConfiguration != this) {
               agentConfiguration.updateConfiguration(harvestConfiguration);
            }
        }
    }

    // return the default instance
    public static AgentConfiguration getInstance() {
        instance.compareAndSet(null, new AgentConfiguration());
        return instance.get();
    }

}