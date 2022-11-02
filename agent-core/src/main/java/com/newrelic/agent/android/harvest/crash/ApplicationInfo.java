/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.crash;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.type.HarvestableObject;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

public class ApplicationInfo extends HarvestableObject {
    private String applicationName = "";
    private String applicationVersion = "";
    private String applicationBuild = "";
    private String bundleId = "";
    private int processId = 0;

    public ApplicationInfo() {
        super();
    }

    public ApplicationInfo(ApplicationInformation applicationInformation) {
        this.applicationName = applicationInformation.getAppName();
        this.applicationVersion = applicationInformation.getAppVersion();
        this.applicationBuild = applicationInformation.getAppBuild();
        this.bundleId = applicationInformation.getPackageId();
    }

    @Override
    public JsonObject asJsonObject() {
        final JsonObject data = new JsonObject();

        /**
         * Per the spec https://newrelic.atlassian.net/wiki/display/eng/Mobile+Agent-Collector+Protocol#MobileAgent-CollectorProtocol-2.1ConnectAPIJSONFormat:
         * Be considerate of adding new elements to the emitted JSON unless also supported by the ApplicationInformation spec.
         */

        data.add("appName", SafeJsonPrimitive.factory(applicationName));
        data.add("appVersion", SafeJsonPrimitive.factory(applicationVersion));
        data.add("appBuild", SafeJsonPrimitive.factory(applicationBuild));
        data.add("bundleId", SafeJsonPrimitive.factory(bundleId));
        data.add("processId", SafeJsonPrimitive.factory(processId));

        return data;
    }

    public static ApplicationInfo newFromJson(JsonObject jsonObject) {
        final ApplicationInfo info = new ApplicationInfo();

        info.applicationName = jsonObject.get("appName").getAsString();
        info.applicationVersion = jsonObject.get("appVersion").getAsString();
        info.applicationBuild = jsonObject.get("appBuild").getAsString();
        info.bundleId = jsonObject.get("bundleId").getAsString();
        info.processId = jsonObject.get("processId").getAsInt();

        return info;
    }
    public String getApplicationName() {
        return applicationName;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public String getApplicationBuild() {
        return applicationBuild;
    }

}
