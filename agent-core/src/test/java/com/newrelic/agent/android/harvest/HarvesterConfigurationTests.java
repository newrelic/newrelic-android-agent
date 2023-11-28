/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newrelic.agent.android.activity.config.ActivityTraceConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfigurationDeserializer;
import com.newrelic.agent.android.logging.LogReporting;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.logging.LoggingConfiguration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.UUID;

@RunWith(JUnit4.class)
public class HarvesterConfigurationTests {

    @Test
    public void testHarvesterConfigurationSerialize() {
        String cross_process_id = "VgMPV1ZTGwIGUFdWAQk=";
        int data_report_period = 60;
        int[] data_token = {1646468, 1997527};
        int error_limit = 50;
        int report_max_transaction_age = 600;
        int report_max_transaction_count = 1000;
        int response_body_limit = 2048;
        long server_timestamp = 1365724800;
        int stack_trace_limit = 100;
        int activity_trace_max_size = 65534;
        int activity_trace_max_report_attempts = 1;
        double activity_trace_min_utilization = 0.3;
        String priority_encoding_key = "d67afc830dab717fd163bfcb0b8b88423e9a1a3b";
        String entityGuid = UUID.randomUUID().toString();

        HarvestConfiguration config = new HarvestConfiguration();

        config.setCollect_network_errors(true);
        config.setCross_process_id(cross_process_id);
        config.setData_report_period(data_report_period);
        config.setData_token(data_token);
        config.setError_limit(error_limit);
        config.setReport_max_transaction_age(report_max_transaction_age);
        config.setReport_max_transaction_count(report_max_transaction_count);
        config.setResponse_body_limit(response_body_limit);
        config.setServer_timestamp(server_timestamp);
        config.setStack_trace_limit(stack_trace_limit);
        config.setActivity_trace_max_size(activity_trace_max_size);
        config.setActivity_trace_max_report_attempts(activity_trace_max_report_attempts);
        config.setActivity_trace_min_utilization(activity_trace_min_utilization);
        config.setPriority_encoding_key(priority_encoding_key);
        config.setAccount_id("1");
        config.setApplication_id("100");
        config.setTrusted_account_key("33");
        config.setLog_reporting(new LogReportingConfiguration(entityGuid, true, LogReporting.LogLevel.WARN));

        Gson gson = new Gson();
        String configJson = gson.toJson(config);

        String expectedJson = "{\"collect_network_errors\":true,\"cross_process_id\":\"VgMPV1ZTGwIGUFdWAQk\\u003d\"," +
                "\"data_report_period\":60,\"data_token\":[1646468,1997527],\"error_limit\":50," +
                "\"report_max_transaction_age\":600,\"report_max_transaction_count\":1000,\"response_body_limit\":2048," +
                "\"server_timestamp\":1365724800,\"stack_trace_limit\":100,\"activity_trace_max_size\":65534," +
                "\"activity_trace_max_report_attempts\":1,\"activity_trace_min_utilization\":0.3,\"at_capture\":{\"maxTotalTraceCount\":1}," +
                "\"priority_encoding_key\":\"d67afc830dab717fd163bfcb0b8b88423e9a1a3b\",\"account_id\":\"1\",\"application_id\":\"100\",\"trusted_account_key\":\"33\"," +
                "\"log_reporting\":{\"entity_guid\":\"" + entityGuid + "\",\"data_report_period\":30,\"expiration_period\":172800,\"enabled\":true,\"level\":\"WARN\"}" +
                "}";

        Assert.assertEquals(expectedJson, configJson);
    }

    @Test
    public void testHarvesterConfigurationDeserialize() {
        String cross_process_id = "VgMPV1ZTGwIGUFdWAQk=";
        int data_report_period = 60;
        int[] data_token = {1646468, 1997527};
        int error_limit = 50;
        int report_max_transaction_age = 600;
        int report_max_transaction_count = 1000;
        int response_body_limit = 2048;
        long server_timestamp = 1365724800;
        int stack_trace_limit = 100;
        String priority_encoding_key = "d67afc830dab717fd163bfcb0b8b88423e9a1a3b";
        String account_id = "1";
        String application_id = "100";
        String trusted_account_key = "33";

        HarvestConfiguration expectedConfig = new HarvestConfiguration();

        expectedConfig.setCollect_network_errors(true);
        expectedConfig.setCross_process_id(cross_process_id);
        expectedConfig.setData_report_period(data_report_period);
        expectedConfig.setData_token(data_token);
        expectedConfig.setError_limit(error_limit);
        expectedConfig.setReport_max_transaction_age(report_max_transaction_age);
        expectedConfig.setReport_max_transaction_count(report_max_transaction_count);
        expectedConfig.setResponse_body_limit(response_body_limit);
        expectedConfig.setServer_timestamp(server_timestamp);
        expectedConfig.setStack_trace_limit(stack_trace_limit);
        expectedConfig.setPriority_encoding_key(priority_encoding_key);
        expectedConfig.setAccount_id(account_id);
        expectedConfig.setApplication_id(application_id);
        expectedConfig.setTrusted_account_key(trusted_account_key);

        String serializedJson = "{\"collect_network_errors\":true,\"cross_process_id\":\"VgMPV1ZTGwIGUFdWAQk\\u003d\"," +
                "\"data_report_period\":60,\"data_token\":[1646468,1997527],\"error_limit\":50," +
                "\"report_max_transaction_age\":600,\"report_max_transaction_count\":1000,\"response_body_limit\":2048," +
                "\"server_timestamp\":1365724800,\"stack_trace_limit\":100," +
                "\"priority_encoding_key\":\"d67afc830dab717fd163bfcb0b8b88423e9a1a3b\",\"account_id\":\"1\"," +
                "\"application_id\":\"100\",\"trusted_account_key\":\"33\"}";

        Gson gson = new Gson();
        HarvestConfiguration config = gson.fromJson(serializedJson, HarvestConfiguration.class);

        Assert.assertEquals(expectedConfig, config);
    }

    @Test
    public void testHarvesterConfigurationDeserializeWithExtraFields() {
        String cross_process_id = "VgMPV1ZTGwIGUFdWAQk=";
        int data_report_period = 60;
        int[] data_token = {1646468, 1997527};
        int error_limit = 50;
        int report_max_transaction_age = 600;
        int report_max_transaction_count = 1000;
        int response_body_limit = 2048;
        long server_timestamp = 1365724800;
        int stack_trace_limit = 100;
        String priority_encoding_key = "d67afc830dab717fd163bfcb0b8b88423e9a1a3b";
        String account_id = "1";
        String application_id = "100";
        String trusted_account_key = "33";

        HarvestConfiguration expectedConfig = new HarvestConfiguration();

        expectedConfig.setCollect_network_errors(true);
        expectedConfig.setCross_process_id(cross_process_id);
        expectedConfig.setData_report_period(data_report_period);
        expectedConfig.setData_token(data_token);
        expectedConfig.setError_limit(error_limit);
        expectedConfig.setReport_max_transaction_age(report_max_transaction_age);
        expectedConfig.setReport_max_transaction_count(report_max_transaction_count);
        expectedConfig.setResponse_body_limit(response_body_limit);
        expectedConfig.setServer_timestamp(server_timestamp);
        expectedConfig.setStack_trace_limit(stack_trace_limit);
        expectedConfig.setPriority_encoding_key(priority_encoding_key);
        expectedConfig.setAccount_id(account_id);
        expectedConfig.setApplication_id(application_id);
        expectedConfig.setTrusted_account_key(trusted_account_key);

        String serializedJson = "{\"collect_network_errors\":true,\"cross_process_id\":\"VgMPV1ZTGwIGUFdWAQk\\u003d\"," +
                "\"data_report_period\":60,\"data_token\":[1646468,1997527],\"error_limit\":50," +
                "\"report_max_transaction_age\":600,\"report_max_transaction_count\":1000,\"response_body_limit\":2048," +
                "\"server_timestamp\":1365724800,\"stack_trace_limit\":100," +
                "\"priority_encoding_key\":\"d67afc830dab717fd163bfcb0b8b88423e9a1a3b\",\"account_id\":\"1\"," +
                "\"application_id\":\"100\",\"trusted_account_key\":\"33\"}";

        Gson gson = new Gson();
        HarvestConfiguration config = gson.fromJson(serializedJson, HarvestConfiguration.class);

        Assert.assertEquals(expectedConfig, config);
    }

    @Test
    public void testHarvesterConfigurationLogReporting() {
        HarvestConfiguration config = new HarvestConfiguration();
        LogReportingConfiguration loggingConfig = new LogReportingConfiguration("dead-beef-bad-f00d", true, LogReporting.LogLevel.VERBOSE);
        config.setLog_reporting(loggingConfig);

        Gson gson = new GsonBuilder().create();

        String configJson = gson.toJson(config.getLog_reporting());
        Assert.assertTrue(configJson.matches("^\\{.*\\}$"));

        String expectedJson = "{\"entity_guid\":\"dead-beef-bad-f00d\",\"data_report_period\":30,\"expiration_period\":172800,\"enabled\":true,\"level\":\"VERBOSE\"}";
        Assert.assertTrue(expectedJson.equals(configJson));
    }

}