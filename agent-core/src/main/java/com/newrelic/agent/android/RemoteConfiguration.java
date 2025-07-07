/**
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.google.gson.annotations.SerializedName;
import com.newrelic.agent.android.aei.ApplicationExitConfiguration;
import com.newrelic.agent.android.harvest.HarvestConfigurable;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration;

/**
 * Data model for agent configuration Json data returned in the Collector connect response.
 * <p>
 * Includes:
 * . FeatureFlag.ApplicationExitReporting
 * . FeatureFlag.LogReporting
 * . FeatureFlag.MobileSessionReplay
 **/
public class RemoteConfiguration implements HarvestLifecycleAware, HarvestConfigurable {

    @SerializedName("application_exit_info")
    protected ApplicationExitConfiguration applicationExitConfiguration;

    @SerializedName("logs")
    protected LogReportingConfiguration logReportingConfiguration;

    @SerializedName("session_replay")
    protected SessionReplayConfiguration sessionReplayConfiguration;

    public RemoteConfiguration() {
        this.applicationExitConfiguration = new ApplicationExitConfiguration(true);
        this.logReportingConfiguration = new LogReportingConfiguration(false, LogLevel.INFO);
        this.sessionReplayConfiguration = new SessionReplayConfiguration();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RemoteConfiguration) {
            RemoteConfiguration rhs = (RemoteConfiguration) obj;
            return (this.applicationExitConfiguration.equals(rhs.applicationExitConfiguration) &&
                    this.logReportingConfiguration.equals(rhs.logReportingConfiguration) &&
                    this.sessionReplayConfiguration.equals(rhs.sessionReplayConfiguration));
        }

        return false;
    }

    public LogReportingConfiguration getLogReportingConfiguration() {
        return logReportingConfiguration;
    }

    public void setLogReportingConfiguration(LogReportingConfiguration logReportingConfiguration) {
        this.logReportingConfiguration = logReportingConfiguration;
    }

    public ApplicationExitConfiguration getApplicationExitConfiguration() {
        return applicationExitConfiguration;
    }

    public void setApplicationExitConfiguration(ApplicationExitConfiguration applicationExitConfiguration) {
        this.applicationExitConfiguration = applicationExitConfiguration;
    }

    public SessionReplayConfiguration getMobileSessionReplayConfiguration() {
        return sessionReplayConfiguration;
    }

    public void setMobileSessionReplayConfiguration(SessionReplayConfiguration sessionReplayConfiguration) {
        this.sessionReplayConfiguration = sessionReplayConfiguration;
    }

    @Override
    public String toString() {
        return "RemoteConfiguration{" +
                "applicationExitConfiguration=" + applicationExitConfiguration +
                ", logReportingConfiguration=" + logReportingConfiguration +
                ", mobileSessionReplayConfiguration=" + sessionReplayConfiguration +
                '}';
    }

    @Override
    public void onHarvestConfigurationChanged() {
        // noop
    }

}