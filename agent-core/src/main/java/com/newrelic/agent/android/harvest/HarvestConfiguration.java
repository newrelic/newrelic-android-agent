/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.annotations.SerializedName;
import com.newrelic.agent.android.RemoteConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is the configuration data format sent by the collector in response to a {@code connect} call.
 */
public class HarvestConfiguration implements HarvestConfigurable {
    protected final static String NO_VALUE = "";

    private final static int DEFAULT_ACTIVITY_TRACE_LENGTH = (64 * 1024) - 1;   // bytes
    private final static int DEFAULT_ACTIVITY_TRACE_MAX_REPORT_ATTEMPTS = 1;
    private final static int DEFAULT_REPORT_PERIOD = 60; // seconds
    private final static int DEFAULT_ERROR_LIMIT = 50; // errors
    private final static int DEFAULT_RESPONSE_BODY_LIMIT = 2048; // bytes
    private final static int DEFAULT_STACK_TRACE_LIMIT = 100; // stack frames
    private final static int DEFAULT_MAX_TRANSACTION_AGE = 600; // seconds
    private final static int DEFAULT_MAX_TRANSACTION_COUNT = 1000; // transactions
    private final static float DEFAULT_ACTIVITY_TRACE_MIN_UTILIZATION = 0.3f;
    private final static String DEFAULT_PRIORITY_ENCODING_KEY = "d67afc830dab717fd163bfcb0b8b88423e9a1a3b";

    // Currently reported (v5)
    @SerializedName("account_id")
    private String account_id;
    @SerializedName("configuration")
    private RemoteConfiguration remote_configuration;
    @SerializedName("data_token")
    private int[] data_token;
    @SerializedName("entity_guid")
    private String entity_guid = NO_VALUE;
    @SerializedName("request_headers_map")
    private Map<String, String> request_headers_map;
    @SerializedName("trusted_account_key")
    private String trusted_account_key;

    // Legacy (v4)
    @SerializedName("collect_network_errors")
    private boolean collect_network_errors;
    @SerializedName("cross_process_id")
    private String cross_process_id;
    @SerializedName("data_report_period")
    private int data_report_period;
    @SerializedName("error_limit")
    private int error_limit;
    @SerializedName("report_max_transaction_age")
    private int report_max_transaction_age;
    @SerializedName("report_max_transaction_count")
    private int report_max_transaction_count;
    @SerializedName("response_body_limit")
    private int response_body_limit;
    @SerializedName("server_timestamp")
    private long server_timestamp;
    @SerializedName("stack_trace_limit")
    private int stack_trace_limit;
    @SerializedName("activity_trace_max_size")
    private int activity_trace_max_size;
    @SerializedName("activity_trace_min_utilization")
    private double activity_trace_min_utilization;
    @SerializedName("at_capture")
    private ActivityTraceConfiguration at_capture;
    @SerializedName(value = "encoding_key", alternate = {"priority_encoding_key"})
    private String encoding_key;
    @SerializedName("application_id")
    private String application_id;

    // deprecated
    @SerializedName("activity_trace_max_report_attempts")
    private int activity_trace_max_report_attempts;

    private static final AtomicReference<HarvestConfiguration> defaultHarvestConfiguration = new AtomicReference<>(null);

    public HarvestConfiguration() {
        setDefaultValues();
    }

    public void setDefaultValues() {
        setData_token(new int[]{0, 0});
        setCollect_network_errors(true);
        setCross_process_id(null);
        setData_report_period(DEFAULT_REPORT_PERIOD);
        setError_limit(DEFAULT_ERROR_LIMIT);
        setResponse_body_limit(DEFAULT_RESPONSE_BODY_LIMIT);
        setStack_trace_limit(DEFAULT_STACK_TRACE_LIMIT);
        setReport_max_transaction_age(DEFAULT_MAX_TRANSACTION_AGE);
        setReport_max_transaction_count(DEFAULT_MAX_TRANSACTION_COUNT);
        setActivity_trace_max_size(DEFAULT_ACTIVITY_TRACE_LENGTH);
        setActivity_trace_max_report_attempts(DEFAULT_ACTIVITY_TRACE_MAX_REPORT_ATTEMPTS);
        setActivity_trace_min_utilization(DEFAULT_ACTIVITY_TRACE_MIN_UTILIZATION);
        setAt_capture(ActivityTraceConfiguration.defaultActivityTraceConfiguration());
        setEncoding_key(DEFAULT_PRIORITY_ENCODING_KEY);
        setAccount_id(NO_VALUE);
        setApplication_id(NO_VALUE);
        setTrusted_account_key(NO_VALUE);
        setEntity_guid(NO_VALUE);
        setRemote_configuration(new RemoteConfiguration());
        setRequest_headers_map(new HashMap<>());
    }

    public static HarvestConfiguration getDefaultHarvestConfiguration() {
        if (defaultHarvestConfiguration.get() == null) {
            defaultHarvestConfiguration.compareAndSet(null, new HarvestConfiguration());
        }

        return defaultHarvestConfiguration.get();
    }

    @Override
    public void setConfiguration(HarvestConfiguration harvestConfiguration) {
        updateConfiguration(harvestConfiguration);
    }

    @Override
    public void updateConfiguration(HarvestConfiguration configuration) {
        setCollect_network_errors(configuration.isCollect_network_errors());

        if (configuration.getCross_process_id() != null) {
            setCross_process_id(configuration.getCross_process_id());
        }

        setData_report_period(configuration.getData_report_period());

        DataToken confDataToken = configuration.getDataToken();
        if (confDataToken != null && confDataToken.isValid()) {
            setData_token(configuration.getData_token());
        }

        setError_limit(configuration.getError_limit());
        setReport_max_transaction_age(configuration.getReport_max_transaction_age());
        setReport_max_transaction_count(configuration.getReport_max_transaction_count());
        setResponse_body_limit(configuration.getResponse_body_limit());
        setServer_timestamp(configuration.getServer_timestamp());
        setStack_trace_limit(configuration.getStack_trace_limit());
        setActivity_trace_min_utilization(configuration.getActivity_trace_min_utilization());
        setActivity_trace_max_report_attempts(configuration.getActivity_trace_max_report_attempts());
        setActivity_trace_max_size(configuration.getActivity_trace_max_size());
        if (configuration.getAt_capture() != null) {
            setAt_capture(configuration.getAt_capture());
        }
        setEncoding_key(configuration.getEncoding_key());
        setAccount_id(configuration.getAccount_id());
        setApplication_id(String.valueOf(configuration.getDataToken().getAccountId()));
        setTrusted_account_key(configuration.getTrusted_account_key());
        setEntity_guid(configuration.getEntity_guid());
        setRemote_configuration(configuration.getRemote_configuration());
        setRequest_headers_map(configuration.getRequest_headers_map());
    }

    public void setCollect_network_errors(boolean collect_network_errors) {
        this.collect_network_errors = collect_network_errors;
    }

    public void setCross_process_id(String cross_process_id) {
        this.cross_process_id = cross_process_id;
    }

    public void setData_report_period(int data_report_period) {
        this.data_report_period = data_report_period;
    }

    public void setData_token(int[] data_token) {
        this.data_token = data_token;
    }

    public DataToken getDataToken() {
        if (data_token == null) {
            return new DataToken(0, 0);
        }
        return new DataToken(data_token[0], data_token[1]);
    }

    public void setError_limit(int error_limit) {
        this.error_limit = error_limit;
    }

    public void setReport_max_transaction_age(int report_max_transaction_age) {
        this.report_max_transaction_age = report_max_transaction_age;
    }

    public void setReport_max_transaction_count(int report_max_transaction_count) {
        this.report_max_transaction_count = report_max_transaction_count;
    }

    public void setResponse_body_limit(int response_body_limit) {
        this.response_body_limit = response_body_limit;
    }

    public void setServer_timestamp(long server_timestamp) {
        this.server_timestamp = server_timestamp;
    }

    public void setStack_trace_limit(int stack_trace_limit) {
        this.stack_trace_limit = stack_trace_limit;
    }

    public void setActivity_trace_max_size(int activity_trace_max_size) {
        this.activity_trace_max_size = activity_trace_max_size;
    }

    public void setActivity_trace_max_report_attempts(int activity_trace_max_report_attempts) {
        this.activity_trace_max_report_attempts = activity_trace_max_report_attempts;
    }

    public boolean isCollect_network_errors() {
        return collect_network_errors;
    }

    public String getCross_process_id() {
        return cross_process_id;
    }

    public int getData_report_period() {
        return data_report_period;
    }

    public int[] getData_token() {
        return data_token;
    }

    public int getError_limit() {
        return error_limit;
    }

    public int getReport_max_transaction_age() {
        return report_max_transaction_age;
    }

    public long getReportMaxTransactionAgeMilliseconds() {
        return TimeUnit.MILLISECONDS.convert(report_max_transaction_age, TimeUnit.SECONDS);
    }

    public int getReport_max_transaction_count() {
        return report_max_transaction_count;
    }

    public int getResponse_body_limit() {
        return response_body_limit;
    }

    public long getServer_timestamp() {
        return server_timestamp;
    }

    public int getStack_trace_limit() {
        return stack_trace_limit;
    }

    public int getActivity_trace_max_size() {
        return activity_trace_max_size;
    }

    public int getActivity_trace_max_report_attempts() {
        return activity_trace_max_report_attempts;
    }

    public ActivityTraceConfiguration getAt_capture() {
        return at_capture;
    }

    public void setAt_capture(ActivityTraceConfiguration at_capture) {
        this.at_capture = at_capture;
    }

    public double getActivity_trace_min_utilization() {
        return activity_trace_min_utilization;
    }

    public void setActivity_trace_min_utilization(double activity_trace_min_utilization) {
        this.activity_trace_min_utilization = activity_trace_min_utilization;
    }

    public String getEncoding_key() {
        return encoding_key;
    }

    public void setEncoding_key(String encoding_key) {
        this.encoding_key = encoding_key;
    }

    public String getApplication_id() {
        if (data_token != null) {
            DataToken dataToken = getDataToken();
            if (dataToken.isValid()) {
                return String.valueOf(dataToken.getAccountId());
            }
        }

        return NO_VALUE;
    }

    public void setApplication_id(String application_id) {
        this.application_id = application_id;
    }

    public String getAccount_id() {
        if (account_id == null) {
            return NO_VALUE;
        }
        return account_id;
    }

    public void setAccount_id(String account_id) {
        this.account_id = account_id;
    }

    public String getTrusted_account_key() {
        if (trusted_account_key == null) {
            return NO_VALUE;
        }
        return trusted_account_key;
    }

    public void setTrusted_account_key(String trusted_account_key) {
        this.trusted_account_key = trusted_account_key;
    }

    public void setEntity_guid(String entityGuid) {
        if (entityGuid == null || entityGuid.isEmpty()) {
            AgentLogManager.getAgentLog().error("setEntity_guid: invalid entity guid value!");
        } else {
            this.entity_guid = entityGuid;
        }
    }

    public void setRemote_configuration(final RemoteConfiguration remoteConfiguration) {
        this.remote_configuration = remoteConfiguration;
    }

    public RemoteConfiguration getRemote_configuration() {
        return this.remote_configuration;
    }

    public Map<String, String> getRequest_headers_map() {
        return request_headers_map;
    }

    public void setRequest_headers_map(Map<String, String> requestHeadersMap) {
        if (requestHeadersMap != null) {
            this.request_headers_map = requestHeadersMap;
        }
    }


    /**
     * Returns the Mobile entity guid synthesized by the Mobile Connect service.
     *
     * @return Entity GUID
     */
    public String getEntity_guid() {
        return entity_guid;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        HarvestConfiguration that = (HarvestConfiguration) o;

        if (collect_network_errors != that.collect_network_errors) {
            return false;
        }
        if (data_report_period != that.data_report_period) {
            return false;
        }
        if (error_limit != that.error_limit) {
            return false;
        }
        if (report_max_transaction_age != that.report_max_transaction_age) {
            return false;
        }
        if (report_max_transaction_count != that.report_max_transaction_count) {
            return false;
        }
        if (response_body_limit != that.response_body_limit) {
            return false;
        }
        if (stack_trace_limit != that.stack_trace_limit) {
            return false;
        }
        if (activity_trace_max_size != that.activity_trace_max_size) {
            return false;
        }
        if (activity_trace_max_report_attempts != that.activity_trace_max_report_attempts) {
            return false;
        }
        if (cross_process_id == null && that.cross_process_id != null) {
            return false;
        }
        if (cross_process_id != null && that.cross_process_id == null) {
            return false;
        }
        if (cross_process_id != null && !cross_process_id.equals(that.cross_process_id)) {
            return false;
        }
        if (!encoding_key.equals(that.encoding_key)) {
            return false;
        }
        if (account_id == null && that.account_id != null) {
            return false;
        }
        if (account_id != null && that.account_id == null) {
            return false;
        }
        if (account_id != null && !account_id.equals(that.account_id)) {
            return false;
        }
        if (getApplication_id() == null && that.getApplication_id() != null) {
            return false;
        }
        if (getApplication_id() != null && that.getApplication_id() == null) {
            return false;
        }
        if (getApplication_id() != null && !getApplication_id().equals(that.getApplication_id())) {
            return false;
        }
        if (trusted_account_key != null && !trusted_account_key.equals(that.trusted_account_key)) {
            return false;
        }
        if (entity_guid != null && !entity_guid.equals(that.entity_guid)) {
            return false;
        }
        if (remote_configuration != null &&
                !remote_configuration.getApplicationExitConfiguration().equals(that.remote_configuration.getApplicationExitConfiguration())) {
            return false;
        }
        if (request_headers_map != null && !request_headers_map.equals(that.request_headers_map)) {
            return false;
        }

        // Round the double value to 2 places.
        int thisMinUtil = (int) activity_trace_min_utilization * 100;
        int thatMinUtil = (int) that.activity_trace_min_utilization * 100;
        if (thisMinUtil != thatMinUtil) {
            return false;
        }

        @SuppressWarnings("UnnecessaryLocalVariable")
        boolean dataTokenEqual = Arrays.equals(data_token, that.data_token);

        return dataTokenEqual;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (collect_network_errors ? 1 : 0);
        result = 31 * result + (cross_process_id != null ? cross_process_id.hashCode() : 0);
        result = 31 * result + data_report_period;
        result = 31 * result + (data_token != null ? Arrays.hashCode(data_token) : 0);
        result = 31 * result + error_limit;
        result = 31 * result + report_max_transaction_age;
        result = 31 * result + report_max_transaction_count;
        result = 31 * result + response_body_limit;
        result = 31 * result + stack_trace_limit;
        result = 31 * result + activity_trace_max_size;
        result = 31 * result + activity_trace_max_report_attempts;
        temp = Double.doubleToLongBits(activity_trace_min_utilization);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (at_capture != null ? at_capture.hashCode() : 0);
        result = 31 * result + (account_id != null ? account_id.hashCode() : 0);
        result = 31 * result + (application_id != null ? application_id.hashCode() : 0);
        result = 31 * result + (encoding_key != null ? encoding_key.hashCode() : 0);
        result = 31 * result + (trusted_account_key != null ? trusted_account_key.hashCode() : 0);
        result = 31 * result + (entity_guid != null ? entity_guid.hashCode() : 0);
        result = 31 * result + (remote_configuration != null ? remote_configuration.hashCode() : 0);
        result = 31 * result + (request_headers_map != null ? request_headers_map.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "HarvestConfiguration{" +
                "collect_network_errors=" + collect_network_errors +
                ", cross_process_id='" + cross_process_id + '\'' +
                ", data_report_period=" + data_report_period +
                ", data_token=" + Arrays.toString(data_token) +
                ", error_limit=" + error_limit +
                ", report_max_transaction_age=" + report_max_transaction_age +
                ", report_max_transaction_count=" + report_max_transaction_count +
                ", response_body_limit=" + response_body_limit +
                ", server_timestamp=" + server_timestamp +
                ", stack_trace_limit=" + stack_trace_limit +
                ", activity_trace_max_size=" + activity_trace_max_size +
                ", activity_trace_max_report_attempts=" + activity_trace_max_report_attempts +
                ", activity_trace_min_utilization=" + activity_trace_min_utilization +
                ", at_capture=" + at_capture +
                ", priority_encoding_key=" + encoding_key +
                ", account_id=" + account_id +
                ", application_id=" + application_id +
                ", trusted_account_key=" + trusted_account_key +
                ", entity_guid=" + entity_guid +
                ", remote_configuration=" + remote_configuration.toString() +
                ", request_headers_map=" + request_headers_map +
                "}";
    }

}
