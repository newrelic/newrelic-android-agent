/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

/**
 * Information used in the {@code connect} phase of harvesting. Contains {@link ApplicationInformation} and {@link DeviceInformation}.
 */
public class ConnectInformation extends HarvestableArray {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private ApplicationInformation applicationInformation;
    private DeviceInformation deviceInformation;

    public ConnectInformation(final ApplicationInformation applicationInformation, final DeviceInformation deviceInformation) {
        if (null == applicationInformation) {
            log.error("null applicationInformation passed into ConnectInformation constructor");
        }
        if (null == deviceInformation) {
            log.error("null deviceInformation passed into ConnectInformation constructor");
        }
        this.applicationInformation = applicationInformation;
        this.deviceInformation = deviceInformation;
    }

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();

        notNull(applicationInformation);
        array.add(applicationInformation.asJsonArray());

        notNull(deviceInformation);
        array.add(deviceInformation.asJsonArray());

        return array;
    }

    public ApplicationInformation getApplicationInformation() {
        return applicationInformation;
    }

    public DeviceInformation getDeviceInformation() {
        return deviceInformation;
    }

    public void setApplicationInformation(final ApplicationInformation applicationInformation) {
        this.applicationInformation = applicationInformation;
    }

    public void setDeviceInformation(final DeviceInformation deviceInformation) {
        this.deviceInformation = deviceInformation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConnectInformation that = (ConnectInformation) o;

        if (applicationInformation != null ? !applicationInformation.equals(that.applicationInformation) : that.applicationInformation != null)
            return false;
        if (deviceInformation != null ? !deviceInformation.equals(that.deviceInformation) : that.deviceInformation != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = applicationInformation != null ? applicationInformation.hashCode() : 0;
        result = 31 * result + (deviceInformation != null ? deviceInformation.hashCode() : 0);
        return result;
    }
}
