/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.HashMap;
import java.util.Map;

public class DeviceInformation extends HarvestableArray {
    private String osName;
    private String osVersion;
    private String osBuild;
    private String model;
    private String agentName;
    private String agentVersion;
    private String deviceId;
    private String countryCode;
    private String regionCode;
    private String manufacturer;
    private String architecture;
    private String runTime;
    private String size;
    private ApplicationFramework applicationFramework;
    private String applicationFrameworkVersion;
    private Map<String, String> misc = new HashMap<String, String>();

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();

        notEmpty(osName);
        array.add(new JsonPrimitive(osName));

        notEmpty(osVersion);
        array.add(new JsonPrimitive(osVersion));

        notEmpty(model);
        array.add(new JsonPrimitive(model));

        notEmpty(agentName);
        array.add(new JsonPrimitive(agentName));

        notEmpty(agentVersion);
        array.add(new JsonPrimitive(agentVersion));

        notEmpty(deviceId);
        array.add(new JsonPrimitive(deviceId));

        // Country code and region are optional
        array.add(new JsonPrimitive(optional(countryCode)));
        array.add(new JsonPrimitive(optional(regionCode)));

        notEmpty(manufacturer);
        array.add(new JsonPrimitive(manufacturer));

        Map<String, String> miscMap = new HashMap<String, String>();
        if (misc != null && !misc.isEmpty()) {
            miscMap.putAll(misc);
        }
        if (applicationFramework != null) {
            miscMap.put("platform", applicationFramework.toString());
            if (applicationFrameworkVersion != null) {
                miscMap.put("platformVersion", applicationFrameworkVersion);
            }
        }

        JsonElement map = new Gson().toJsonTree(miscMap, GSON_STRING_MAP_TYPE);
        array.add(map);

        return array;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public void setOsBuild(String osBuild) {
        this.osBuild = osBuild;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public void setRunTime(String runTime) {
        this.runTime = runTime;
    }

    public void setSize(String size) {
        this.size = size;
        addMisc("size", size);
    }

    public void setApplicationFramework(ApplicationFramework ApplicationFramework) {
        this.applicationFramework = ApplicationFramework;
    }

    public void setApplicationFrameworkVersion(String ApplicationFrameworkVersion) {
        this.applicationFrameworkVersion = ApplicationFrameworkVersion;
    }

    public void setMisc(Map<String, String> misc) {
        this.misc = new HashMap<String, String>(misc);
    }

    public void addMisc(String key, String value) {
        misc.put(key, value);
    }

    public String getOsName() {
        return osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getOsBuild() {
        return osBuild;
    }

    public String getModel() {
        return model;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getArchitecture() {
        return architecture;
    }

    public String getRunTime() {
        return runTime;
    }

    public String getSize() {
        return size;
    }

    public ApplicationFramework getApplicationFramework() {
        return applicationFramework;
    }

    public String getApplicationFrameworkVersion() {
        return applicationFrameworkVersion;
    }

    @Override
    public String toJsonString() {
        return "DeviceInformation{" +
                "manufacturer='" + manufacturer + '\'' +
                ", osName='" + osName + '\'' +
                ", osVersion='" + osVersion + '\'' +
                ", model='" + model + '\'' +
                ", agentName='" + agentName + '\'' +
                ", agentVersion='" + agentVersion + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", regionCode='" + regionCode + '\'' +
                '}';
    }

    // Compare equality to saved state using everything but 'misc', 'countryCode' and 'regionCode'
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeviceInformation that = (DeviceInformation) o;

        if (agentName != null ? !agentName.equals(that.agentName) : that.agentName != null) return false;
        if (agentVersion != null ? !agentVersion.equals(that.agentVersion) : that.agentVersion != null) return false;
        if (architecture != null ? !architecture.equals(that.architecture) : that.architecture != null) return false;
        if (deviceId != null ? !deviceId.equals(that.deviceId) : that.deviceId != null) return false;
        if (manufacturer != null ? !manufacturer.equals(that.manufacturer) : that.manufacturer != null) return false;
        if (model != null ? !model.equals(that.model) : that.model != null) return false;
        if (osBuild != null ? !osBuild.equals(that.osBuild) : that.osBuild != null) return false;
        if (osName != null ? !osName.equals(that.osName) : that.osName != null) return false;
        if (osVersion != null ? !osVersion.equals(that.osVersion) : that.osVersion != null) return false;
        if (runTime != null ? !runTime.equals(that.runTime) : that.runTime != null) return false;
        if (size != null ? !size.equals(that.size) : that.size != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = osName != null ? osName.hashCode() : 0;
        result = 31 * result + (osVersion != null ? osVersion.hashCode() : 0);
        result = 31 * result + (osBuild != null ? osBuild.hashCode() : 0);
        result = 31 * result + (model != null ? model.hashCode() : 0);
        result = 31 * result + (agentName != null ? agentName.hashCode() : 0);
        result = 31 * result + (agentVersion != null ? agentVersion.hashCode() : 0);
        result = 31 * result + (deviceId != null ? deviceId.hashCode() : 0);
        result = 31 * result + (manufacturer != null ? manufacturer.hashCode() : 0);
        result = 31 * result + (architecture != null ? architecture.hashCode() : 0);
        result = 31 * result + (runTime != null ? runTime.hashCode() : 0);
        result = 31 * result + (size != null ? size.hashCode() : 0);
        return result;
    }
}
