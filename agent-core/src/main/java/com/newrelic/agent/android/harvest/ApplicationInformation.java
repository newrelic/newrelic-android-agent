/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableArray;

/**
 * Contains application information such as application name, version and package. This information is used as a part
 * of {@link ConnectInformation} which is sent to the collector during the connect phase.
 */
public class ApplicationInformation extends HarvestableArray {
    private String appName;
    private String appVersion;
    private String appBuild;
    private String packageId;

    private int versionCode = -1;       // not reported as app info. do not serialize to/from json

    public ApplicationInformation() {
        super();
    }

    public ApplicationInformation(String appName, String appVersion, String packageId, String appBuild) {
        this();
        this.appName = appName;
        this.appVersion = appVersion;
        this.packageId = packageId;
        this.appBuild = appBuild;
    }

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();

        /**
         * Per the spec https://newrelic.atlassian.net/wiki/display/eng/Mobile+Agent-Collector+Protocol#MobileAgent-CollectorProtocol-2.1ConnectAPIJSONFormat:
         * Do not add additional elements to the emitted JSON unless also supported by the ApplicationInfo spec.
         */

        notEmpty(appName);
        array.add(new JsonPrimitive(appName));
        notEmpty(appVersion);
        array.add(new JsonPrimitive(appVersion));
        notEmpty(packageId);
        array.add(new JsonPrimitive(packageId));

        return array;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppBuild(String appBuild) {
        this.appBuild = appBuild;
    }

    public String getAppBuild() {
        return appBuild;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public int getVersionCode() {
        return versionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ApplicationInformation that = (ApplicationInformation) o;

        if (appName != null ? !appName.equals(that.appName) : that.appName != null) return false;
        if (appVersion != null ? !appVersion.equals(that.appVersion) : that.appVersion != null) return false;
        if (appBuild != null ? !appBuild.equals(that.appBuild) : that.appBuild != null) return false;
        if (packageId != null ? !packageId.equals(that.packageId) : that.packageId != null) return false;
        if (versionCode != that.versionCode) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appName != null ? appName.hashCode() : 0;
        result = 31 * result + (appVersion != null ? appVersion.hashCode() : 0);
        result = 31 * result + (appBuild != null ? appBuild.hashCode() : 0);
        result = 31 * result + (packageId != null ? packageId.hashCode() : 0);
        return result;
    }

    public boolean isAppUpgrade(ApplicationInformation that) {
        boolean brc = false;

        // that.versionCode == -1 && that.appVersion != null : upgrade from older version that didn't track versionCode
        // that.versionCode == -1 && this.versionCode >= 0 : newly installed app (no upgrade)
        // this.versionCode > that.versionCode : upgrade!

        if (that.versionCode == -1) {
            brc = (this.versionCode >= 0) && (that.appVersion != null);
        } else {
            brc = this.versionCode > that.versionCode;
        }

        return brc;
    }
}
