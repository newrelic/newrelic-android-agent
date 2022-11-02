/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.ConnectInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SavedState extends HarvestAdapter {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private final String PREFERENCE_FILE_PREFIX = "com.newrelic.android.agent.v1_";

    // Harvest configuration
    private final String PREF_MAX_TRANSACTION_COUNT = "maxTransactionCount";
    private final String PREF_MAX_TRANSACTION_AGE = "maxTransactionAgeInSeconds";
    private final String PREF_HARVEST_INTERVAL = "harvestIntervalInSeconds";
    private final String PREF_SERVER_TIMESTAMP = "serverTimestamp";
    private final String PREF_CROSS_PROCESS_ID = "crossProcessId";
    private final String PREF_PRIORITY_ENCODING_KEY = "encoding_key";
    private final String PREF_ACCOUNT_ID = "account_id";
    private final String PREF_APPLICATION_ID = "application_id";
    private final String PREF_DATA_TOKEN = "dataToken";
    private final String PREF_CONNECT_HASH = "connectHash";
    private final String PREF_STACK_TRACE_LIMIT = "stackTraceLimit";
    private final String PREF_RESPONSE_BODY_LIMIT = "responseBodyLimit";
    private final String PREF_COLLECT_NETWORK_ERRORS = "collectNetworkErrors";
    private final String PREF_ERROR_LIMIT = "errorLimit";
    private final String NEW_RELIC_AGENT_DISABLED_VERSION_KEY = "NewRelicAgentDisabledVersion";
    private final String PREF_ACTIVITY_TRACE_MIN_UTILIZATION = "activityTraceMinUtilization";
    private Float activityTraceMinUtilization;

    private final HarvestConfiguration configuration = new HarvestConfiguration();

    // Connect information
    private final String PREF_APP_NAME = "appName";
    private final String PREF_APP_VERSION = "appVersion";
    private final String PREF_APP_BUILD = "appBuild";
    private final String PREF_PACKAGE_ID = "packageId";
    private final String PREF_VERSION_CODE = "versionCode";
    private final String PREF_AGENT_NAME = "agentName";
    private final String PREF_AGENT_VERSION = "agentVersion";
    private final String PREF_DEVICE_ARCHITECTURE = "deviceArchitecture";
    private final String PREF_DEVICE_ID = "deviceId";
    private final String PREF_DEVICE_MODEL = "deviceModel";
    private final String PREF_DEVICE_MANUFACTURER = "deviceManufacturer";
    private final String PREF_DEVICE_RUN_TIME = "deviceRunTime";
    private final String PREF_DEVICE_SIZE = "deviceSize";
    private final String PREF_OS_NAME = "osName";
    private final String PREF_OS_BUILD = "osBuild";
    private final String PREF_OS_VERSION = "osVersion";
    private final String PREF_PLATFORM = "platform";
    private final String PREF_PLATFORM_VERSION = "platformVersion";

    private final ConnectInformation connectInformation = new ConnectInformation(new ApplicationInformation(), new DeviceInformation());

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;
    private final Lock lock = new ReentrantLock();

    @SuppressLint("CommitPrefEdits")
    public SavedState(Context context) {
        prefs = context.getSharedPreferences(getPreferenceFileName(context.getPackageName()), 0);
        editor = prefs.edit();
        loadHarvestConfiguration();
        loadConnectInformation();
    }

    public void saveHarvestConfiguration(HarvestConfiguration newConfiguration) {
        // If the new configuration is the same as the current, skip saving.
        if (configuration.equals(newConfiguration))
            return;

        if (!newConfiguration.getDataToken().isValid())
            newConfiguration.setData_token(configuration.getData_token());

        log.info("Saving configuration: " + newConfiguration);

        final String newDataToken = newConfiguration.getDataToken().toJsonString();
        log.debug("!! saving data token: " + newDataToken);
        save(PREF_DATA_TOKEN, newDataToken);
        save(PREF_CROSS_PROCESS_ID, newConfiguration.getCross_process_id());
        save(PREF_SERVER_TIMESTAMP, newConfiguration.getServer_timestamp());
        save(PREF_HARVEST_INTERVAL, (long) newConfiguration.getData_report_period());
        save(PREF_MAX_TRANSACTION_AGE, (long) newConfiguration.getReport_max_transaction_age());
        save(PREF_MAX_TRANSACTION_COUNT, (long) newConfiguration.getReport_max_transaction_count());
        save(PREF_STACK_TRACE_LIMIT, newConfiguration.getStack_trace_limit());
        save(PREF_RESPONSE_BODY_LIMIT, newConfiguration.getResponse_body_limit());
        save(PREF_COLLECT_NETWORK_ERRORS, newConfiguration.isCollect_network_errors());
        save(PREF_ERROR_LIMIT, newConfiguration.getError_limit());
        save(PREF_PRIORITY_ENCODING_KEY, newConfiguration.getPriority_encoding_key());
        save(PREF_ACCOUNT_ID, newConfiguration.getAccount_id());
        save(PREF_APPLICATION_ID, newConfiguration.getApplication_id());
        saveActivityTraceMinUtilization((float) newConfiguration.getActivity_trace_min_utilization());

        // Reload the configuration
        loadHarvestConfiguration();
    }

    public void loadHarvestConfiguration() {
        if (has(PREF_DATA_TOKEN))
            configuration.setData_token(getDataToken());
        if (has(PREF_CROSS_PROCESS_ID))
            configuration.setCross_process_id(getCrossProcessId());
        if (has(PREF_PRIORITY_ENCODING_KEY))
            configuration.setPriority_encoding_key(getPriorityEncodingKey());
        if (has(PREF_ACCOUNT_ID))
            configuration.setAccount_id(getAccountId());
        if(has(PREF_APPLICATION_ID))
            configuration.setApplication_id(getApplicationId());
        if (has(PREF_SERVER_TIMESTAMP))
            configuration.setServer_timestamp(getServerTimestamp());
        if (has(PREF_HARVEST_INTERVAL))
            configuration.setData_report_period((int) getHarvestIntervalInSeconds());
        if (has(PREF_MAX_TRANSACTION_AGE))
            configuration.setReport_max_transaction_age((int) getMaxTransactionAgeInSeconds());
        if (has(PREF_MAX_TRANSACTION_COUNT))
            configuration.setReport_max_transaction_count((int) getMaxTransactionCount());
        if (has(PREF_STACK_TRACE_LIMIT))
            configuration.setStack_trace_limit(getStackTraceLimit());
        if (has(PREF_RESPONSE_BODY_LIMIT))
            configuration.setResponse_body_limit(getResponseBodyLimit());
        if (has(PREF_COLLECT_NETWORK_ERRORS))
            configuration.setCollect_network_errors(isCollectingNetworkErrors());
        if (has(PREF_ERROR_LIMIT))
            configuration.setError_limit(getErrorLimit());
        if (has(PREF_ACTIVITY_TRACE_MIN_UTILIZATION))
            configuration.setActivity_trace_min_utilization(getActivityTraceMinUtilization());
        if (has(PREF_PRIORITY_ENCODING_KEY))
            configuration.setPriority_encoding_key(getPriorityEncodingKey());
        if (has(PREF_ACCOUNT_ID))
            configuration.setAccount_id(getAccountId());
        if (has(PREF_APPLICATION_ID))
            configuration.setApplication_id(getApplicationId());

        log.info("Loaded configuration: " + configuration);
    }

    public void saveConnectInformation(final ConnectInformation newConnectInformation) {
        if (connectInformation.equals(newConnectInformation))
            return;

        saveApplicationInformation(newConnectInformation.getApplicationInformation());
        saveDeviceInformation(newConnectInformation.getDeviceInformation());
        // Reload the connect information
        loadConnectInformation();
    }

    public void saveDeviceId(final String deviceId) {
        save(PREF_DEVICE_ID, deviceId);
        connectInformation.getDeviceInformation().setDeviceId(deviceId);
    }

    public String getConnectionToken() {
        return String.valueOf(getInt(PREF_CONNECT_HASH));
    }

    public void saveConnectionToken(final String connectionToken) {
        save(PREF_CONNECT_HASH, connectionToken.hashCode());
    }

    private void saveApplicationInformation(final ApplicationInformation applicationInformation) {
        save(PREF_APP_NAME, applicationInformation.getAppName());
        save(PREF_APP_VERSION, applicationInformation.getAppVersion());
        save(PREF_APP_BUILD, applicationInformation.getAppBuild());
        save(PREF_PACKAGE_ID, applicationInformation.getPackageId());
        save(PREF_VERSION_CODE, applicationInformation.getVersionCode());
    }

    private void saveDeviceInformation(final DeviceInformation deviceInformation) {
        save(PREF_AGENT_NAME, deviceInformation.getAgentName());
        save(PREF_AGENT_VERSION, deviceInformation.getAgentVersion());
        save(PREF_DEVICE_ARCHITECTURE, deviceInformation.getArchitecture());
        save(PREF_DEVICE_ID, deviceInformation.getDeviceId());
        save(PREF_DEVICE_MODEL, deviceInformation.getModel());
        save(PREF_DEVICE_MANUFACTURER, deviceInformation.getManufacturer());
        save(PREF_DEVICE_RUN_TIME, deviceInformation.getRunTime());
        save(PREF_DEVICE_SIZE, deviceInformation.getSize());
        save(PREF_OS_NAME, deviceInformation.getOsName());
        save(PREF_OS_BUILD, deviceInformation.getOsBuild());
        save(PREF_OS_VERSION, deviceInformation.getOsVersion());
        save(PREF_PLATFORM, deviceInformation.getApplicationFramework().toString());
        save(PREF_PLATFORM_VERSION, deviceInformation.getApplicationFrameworkVersion());
    }

    public void loadConnectInformation() {
        final ApplicationInformation applicationInformation = new ApplicationInformation();
        if (has(PREF_APP_NAME))
            applicationInformation.setAppName(getAppName());
        if (has(PREF_APP_VERSION))
            applicationInformation.setAppVersion(getAppVersion());
        if (has(PREF_APP_BUILD))
            applicationInformation.setAppBuild(getAppBuild());
        if (has(PREF_PACKAGE_ID))
            applicationInformation.setPackageId(getPackageId());
        if (has(PREF_VERSION_CODE))
            applicationInformation.setVersionCode(getVersionCode());

        final DeviceInformation deviceInformation = new DeviceInformation();
        if (has(PREF_AGENT_NAME))
            deviceInformation.setAgentName(getAgentName());
        if (has(PREF_AGENT_VERSION))
            deviceInformation.setAgentVersion(getAgentVersion());
        if (has(PREF_DEVICE_ARCHITECTURE))
            deviceInformation.setArchitecture(getDeviceArchitecture());
        if (has(PREF_DEVICE_ID))
            deviceInformation.setDeviceId(getDeviceId());
        if (has(PREF_DEVICE_MODEL))
            deviceInformation.setModel(getDeviceModel());
        if (has(PREF_DEVICE_MANUFACTURER))
            deviceInformation.setManufacturer(getDeviceManufacturer());
        if (has(PREF_DEVICE_RUN_TIME))
            deviceInformation.setRunTime(getDeviceRunTime());
        if (has(PREF_DEVICE_SIZE))
            deviceInformation.setSize(getDeviceSize());
        if (has(PREF_OS_NAME))
            deviceInformation.setOsName(getOsName());
        if (has(PREF_OS_BUILD))
            deviceInformation.setOsBuild(getOsBuild());
        if (has(PREF_OS_VERSION))
            deviceInformation.setOsVersion(getOsVersion());
        if (has(PREF_PLATFORM))
            deviceInformation.setApplicationFramework(getFramework());
        if (has(PREF_PLATFORM_VERSION))
            deviceInformation.setApplicationFrameworkVersion(getPlatformVersion());

        connectInformation.setApplicationInformation(applicationInformation);
        connectInformation.setDeviceInformation(deviceInformation);
    }

    public HarvestConfiguration getHarvestConfiguration() {
        return configuration;
    }

    public ConnectInformation getConnectInformation() {
        return connectInformation;
    }

    private boolean has(String key) {
        return prefs.contains(key);
    }

    @Override
    public void onHarvestConnected() {
        saveHarvestConfiguration(Harvest.getHarvestConfiguration());
    }

    @Override
    public void onHarvestComplete() {
    }

    @Override
    public void onHarvestDisconnected() {
        log.info("Clearing harvest configuration.");
        clear();
    }

    @Override
    public void onHarvestDisabled() {
        String agentVersion = Agent.getDeviceInformation().getAgentVersion();
        log.info("Disabling agent version " + agentVersion);
        saveDisabledVersion(agentVersion);
    }

    public void save(String key, String value) {
        lock.lock();
        try {
            editor.putString(key, value);
            editor.apply();
        } finally {
            lock.unlock();
        }
    }

    public void save(String key, boolean value) {
        lock.lock();
        try {
            editor.putBoolean(key, value);
            editor.apply();
        } finally {
            lock.unlock();
        }
    }

    public void save(String key, int value) {
        lock.lock();
        try {
            editor.putInt(key, value);
            editor.apply();
        } finally {
            lock.unlock();
        }
    }

    public void save(String key, long value) {
        lock.lock();
        try {
            editor.putLong(key, value);
            editor.apply();
        } finally {
            lock.unlock();
        }
    }

    public void save(String key, float value) {
        lock.lock();
        try {
            editor.putFloat(key, value);
            editor.apply();
        } finally {
            lock.unlock();
        }
    }

    public String getString(String key) {
        if (!prefs.contains(key))
            return null;

        return prefs.getString(key, null);
    }

    public boolean getBoolean(String key) {
        return prefs.getBoolean(key, false);
    }

    public long getLong(String key) {
        return prefs.getLong(key, 0);
    }

    public int getInt(String key) {
        return prefs.getInt(key, 0);
    }

    public Float getFloat(String key) {
        if (!prefs.contains(key))
            return null;

        float f = prefs.getFloat(key, 0.0f);

        // Round the float value to 2 decimal places. Float values read from prefs have float noise in the low bits.
        return ((int) (f * 100)) / 100.0f;
    }

    public String getDisabledVersion() {
        return getString(NEW_RELIC_AGENT_DISABLED_VERSION_KEY);
    }

    public void saveDisabledVersion(String version) {
        save(NEW_RELIC_AGENT_DISABLED_VERSION_KEY, version);
    }

    public int[] getDataToken() {
        int[] dataToken = new int[2];
        String dataTokenString = getString(PREF_DATA_TOKEN);

        if (dataTokenString == null)
            return null;

        try {
            JSONTokener tokener = new JSONTokener(dataTokenString);
            if (tokener == null)
                return null;

            JSONArray array = (JSONArray) tokener.nextValue();

            if (array == null)
                return null;

            dataToken[0] = array.getInt(0);
            dataToken[1] = array.getInt(1);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return dataToken;
    }

    // Harvest configuration
    public String getCrossProcessId() {
        return getString(PREF_CROSS_PROCESS_ID);
    }

    public String getPriorityEncodingKey() {
        return getString(PREF_PRIORITY_ENCODING_KEY);
    }

    public String getAccountId() {
        return getString(PREF_ACCOUNT_ID);
    }

    public String getApplicationId() {
        return getString(PREF_APPLICATION_ID);
    }

    public boolean isCollectingNetworkErrors() {
        return getBoolean(PREF_COLLECT_NETWORK_ERRORS);
    }

    public long getServerTimestamp() {
        return getLong(PREF_SERVER_TIMESTAMP);
    }

    public long getHarvestInterval() {
        return getLong(PREF_HARVEST_INTERVAL);
    }

    public long getMaxTransactionAge() {
        return getLong(PREF_MAX_TRANSACTION_AGE);
    }

    public long getMaxTransactionCount() {
        return getLong(PREF_MAX_TRANSACTION_COUNT);
    }

    public int getStackTraceLimit() {
        return getInt(PREF_STACK_TRACE_LIMIT);
    }

    public int getResponseBodyLimit() {
        return getInt(PREF_RESPONSE_BODY_LIMIT);
    }

    public int getErrorLimit() {
        return getInt(PREF_ERROR_LIMIT);
    }

    public void saveActivityTraceMinUtilization(float activityTraceMinUtilization) {
        this.activityTraceMinUtilization = activityTraceMinUtilization;
        save(PREF_ACTIVITY_TRACE_MIN_UTILIZATION, activityTraceMinUtilization);
    }

    public float getActivityTraceMinUtilization() {
        if (activityTraceMinUtilization == null)
            activityTraceMinUtilization = getFloat(PREF_ACTIVITY_TRACE_MIN_UTILIZATION);
        return activityTraceMinUtilization;
    }

    public long getHarvestIntervalInSeconds() {
        return getHarvestInterval();
    }


    public long getMaxTransactionAgeInSeconds() {
        return getMaxTransactionAge();
    }

    // Connect information
    public String getAppName() {
        return getString(PREF_APP_NAME);
    }

    public String getAppVersion() {
        return getString(PREF_APP_VERSION);
    }

    public int getVersionCode() {
        return getInt(PREF_VERSION_CODE);
    }

    public String getAppBuild() {
        return getString(PREF_APP_BUILD);
    }

    public String getPackageId() {
        return getString(PREF_PACKAGE_ID);
    }

    public String getAgentName() {
        return getString(PREF_AGENT_NAME);
    }

    public String getAgentVersion() {
        return getString(PREF_AGENT_VERSION);
    }

    public String getDeviceArchitecture() {
        return getString(PREF_DEVICE_ARCHITECTURE);
    }

    public String getDeviceId() {
        return getString(PREF_DEVICE_ID);
    }

    public String getDeviceModel() {
        return getString(PREF_DEVICE_MODEL);
    }

    public String getDeviceManufacturer() {
        return getString(PREF_DEVICE_MANUFACTURER);
    }

    public String getDeviceRunTime() {
        return getString(PREF_DEVICE_RUN_TIME);
    }

    public String getDeviceSize() {
        return getString(PREF_DEVICE_SIZE);
    }

    public String getOsName() {
        return getString(PREF_OS_NAME);
    }

    public String getOsBuild() {
        return getString(PREF_OS_BUILD);
    }

    public String getOsVersion() {
        return getString(PREF_OS_VERSION);
    }

    public String getApplicationFramework() {
        return getString(PREF_PLATFORM);
    }

    public String getApplicationFrameworkVersion() {
        return getString(PREF_PLATFORM_VERSION);
    }

    public ApplicationFramework getFramework() {
        ApplicationFramework applicationFramework = ApplicationFramework.Native;
        try {
            applicationFramework =  ApplicationFramework.valueOf(getString(PREF_PLATFORM));
        } catch (IllegalArgumentException e) {
        }
        return applicationFramework;
    }

    public String getPlatformVersion() {
        return getString(PREF_PLATFORM_VERSION);
    }

    private String getPreferenceFileName(final String packageName) {
        return PREFERENCE_FILE_PREFIX + packageName;
    }

    public void clear() {
        lock.lock();
        try {
            editor.clear();
            editor.apply();
            configuration.setDefaultValues();
        } finally {
            lock.unlock();
        }
    }

    public boolean hasConnectionToken(final String appToken) {
        return getInt(PREF_CONNECT_HASH) == appToken.hashCode();
    }
}
