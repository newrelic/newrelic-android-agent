/**
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.google.gson.annotations.SerializedName;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.logging.LoggingConfiguration;

/**
 * Data model for agent configuration Json data returned in the Collector connect response.
 *
 * Includes:
 *  . FeatureFlag.ApplicationExitReporting
 *
 **/
public class RemoteConfiguration implements HarvestLifecycleAware {

    @SerializedName("logging")
    protected LogReportingConfiguration logReportingConfiguration;

    @SerializedName("application_exit_info")
    protected ApplicationExitConfiguration applicationExitConfiguration;

    public RemoteConfiguration() {
        this.logReportingConfiguration = new LogReportingConfiguration();
        this.applicationExitConfiguration = new ApplicationExitConfiguration();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RemoteConfiguration) {
            RemoteConfiguration rhs = (RemoteConfiguration) obj;
            return this == rhs;
        }

        return false;
    }

    public LogReportingConfiguration getLogReportingConfiguration() {
        return logReportingConfiguration;
    }

    public ApplicationExitConfiguration getApplicationExitConfiguration() {
        return applicationExitConfiguration;
    }

    public void setLogReportingConfiguration(LogReportingConfiguration logReportingConfiguration) {
        this.logReportingConfiguration = logReportingConfiguration;
    }

    public void setApplicationExitConfiguration(ApplicationExitConfiguration applicationExitConfiguration) {
        this.applicationExitConfiguration = applicationExitConfiguration;
    }

    @Override
    public void onHarvestConfigurationChanged() {
        // noop
    }

}