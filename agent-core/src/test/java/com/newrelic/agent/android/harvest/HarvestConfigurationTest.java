/*
 * Copyright (c) 2022-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newrelic.agent.android.RemoteConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfigurationDeserializer;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.UUID;

@RunWith(JUnit4.class)
public class HarvestConfigurationTest {
    final String entityGuid = UUID.randomUUID().toString();
    final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ActivityTraceConfiguration.class, new ActivityTraceConfigurationDeserializer())
            .create();

    @Test
    public void testHarvestConfigurationSerialize() {
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
        String encoding_key = "d67afc830dab717fd163bfcb0b8b88423e9a1a3b";

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
        config.setEncoding_key(encoding_key);
        config.setAccount_id("1");
        config.setApplication_id("100");
        config.setTrusted_account_key("33");
        config.setEntity_guid(entityGuid);
        config.setRemote_configuration(new RemoteConfiguration());
        config.setRequest_headers_map(null);

        String configJson = gson.toJson(config);
        String expectedJson = "{" +
                "\"account_id\":\"1\"," +
                "\"configuration\":{\"application_exit_info\":{\"enabled\":true},\"logs\":{\"data_report_period\":30,\"expiration_period\":172800,\"enabled\":true,\"level\":\"INFO\"}}," +
                "\"data_token\":[1646468,1997527]," +
                "\"entity_guid\":\"" + entityGuid + "\"," +
                "\"request_headers_map\":{}," +
                "\"trusted_account_key\":\"33\"," +
                "\"collect_network_errors\":true," +
                "\"cross_process_id\":\"VgMPV1ZTGwIGUFdWAQk\\u003d\"," +
                "\"data_report_period\":60," +
                "\"error_limit\":50," +
                "\"report_max_transaction_age\":600," +
                "\"report_max_transaction_count\":1000," +
                "\"response_body_limit\":2048," +
                "\"server_timestamp\":1365724800," +
                "\"stack_trace_limit\":100," +
                "\"activity_trace_max_size\":65534," +
                "\"activity_trace_min_utilization\":0.3," +
                "\"at_capture\":{\"maxTotalTraceCount\":1}," +
                "\"encoding_key\":\"d67afc830dab717fd163bfcb0b8b88423e9a1a3b\"," +
                "\"application_id\":\"100\"," +
                "\"activity_trace_max_report_attempts\":1" +
                "}";

        Assert.assertEquals(expectedJson, configJson);
    }

    @Test
    public void testHarvestConfigurationDeserialize() {
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
        expectedConfig.setEncoding_key(priority_encoding_key);
        expectedConfig.setAccount_id(account_id);
        expectedConfig.setApplication_id(application_id);
        expectedConfig.setTrusted_account_key(trusted_account_key);
        expectedConfig.setEntity_guid(entityGuid);
        expectedConfig.setRemote_configuration(new RemoteConfiguration());

        String expectedJson = "{" +
                "\"collect_network_errors\":true," +
                "\"cross_process_id\":\"VgMPV1ZTGwIGUFdWAQk\\u003d\"," +
                "\"data_report_period\":60," +
                "\"data_token\":[1646468,1997527]," +
                "\"error_limit\":50," +
                "\"report_max_transaction_age\":600," +
                "\"report_max_transaction_count\":1000," +
                "\"response_body_limit\":2048," +
                "\"server_timestamp\":1365724800," +
                "\"stack_trace_limit\":100," +
                "\"priority_encoding_key\":\"d67afc830dab717fd163bfcb0b8b88423e9a1a3b\"," +
                "\"account_id\":\"1\"," +
                "\"application_id\":1," +
                "\"trusted_account_key\":\"33\"," +
                "\"entity_guid\":\"" + entityGuid + "\"," +
                "\"configuration\":{\"application_exit_info\":{\"enabled\":true}}" +
                "}";

        HarvestConfiguration config = gson.fromJson(expectedJson, HarvestConfiguration.class);

        Assert.assertTrue(expectedConfig.equals(config));
    }

    @Test
    public void testHarvestConfigurationDeserializeWithExtraFields() {
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
        expectedConfig.setEncoding_key(priority_encoding_key);
        expectedConfig.setAccount_id(account_id);
        expectedConfig.setApplication_id(application_id);
        expectedConfig.setTrusted_account_key(trusted_account_key);
        expectedConfig.setEntity_guid(entityGuid);

        String serializedJson = "{\" +" +
                "collect_network_errors\":true," +
                "\"cross_process_id\":\"VgMPV1ZTGwIGUFdWAQk\\u003d\"," +
                "\"data_report_period\":60," +
                "\"data_token\":[1646468,1997527]," +
                "\"error_limit\":50," +
                "\"report_max_transaction_age\":600," +
                "\"report_max_transaction_count\":1000," +
                "\"response_body_limit\":2048," +
                "\"server_timestamp\":1365724800," +
                "\"stack_trace_limit\":100," +
                "\"priority_encoding_key\":\"d67afc830dab717fd163bfcb0b8b88423e9a1a3b\"," +
                "\"account_id\":\"1\"," +
                "\"application_id\":100," +
                "\"trusted_account_key\":\"33\"," +
                "\"entity_guid\":\"" + entityGuid + "\"" +
                "}";

        HarvestConfiguration config = gson.fromJson(serializedJson, HarvestConfiguration.class);

        Assert.assertEquals(expectedConfig, config);
    }

    @Test
    public void testHarvestConfigurationRemoteConfig() {
        HarvestConfiguration config = new HarvestConfiguration();
        RemoteConfiguration remoteConfiguration = new RemoteConfiguration();
        config.setRemote_configuration(remoteConfiguration);

        String configJson = gson.toJson(config.getRemote_configuration());
        Assert.assertTrue(configJson.matches("^\\{.*\\}$"));

        String expectedJson = "{\"application_exit_info\":{\"enabled\":false}}";
        Assert.assertFalse(expectedJson.equals(configJson));
    }

    @Test
    public void testReconfigure() {
        HarvestConfiguration config = new HarvestConfiguration();
        Assert.assertTrue(config.getRequest_headers_map().isEmpty());

        config.getRequest_headers_map().put("Header a", "VALUE a");
        config.getRequest_headers_map().put("Header 2", "vAlue 2");
        Assert.assertEquals(2, config.getRequest_headers_map().size());

        HarvestConfiguration providedConfig = Providers.provideHarvestConfiguration();
        Assert.assertFalse(config.equals(providedConfig));

        config.updateConfiguration(providedConfig);
        Assert.assertTrue(config.equals(providedConfig));
        Assert.assertEquals(2, config.getRequest_headers_map().size());
    }

    @Test
    public void setDefaultValues() {
        HarvestConfiguration config = Providers.provideHarvestConfiguration();
        Assert.assertNotEquals(config.getDataToken().toString(), new DataToken(0, 0).toString());
        Assert.assertNotEquals(0, config.getData_token()[0]);
        Assert.assertNotEquals(0, config.getData_token()[1]);
        Assert.assertFalse(config.getRequest_headers_map().isEmpty());

        config.setDefaultValues();
        Assert.assertEquals(config.getDataToken().toString(), new DataToken(0, 0).toString());
        Assert.assertEquals(0, config.getData_token()[0]);
        Assert.assertEquals(0, config.getData_token()[1]);
        Assert.assertEquals(config.getRemote_configuration(), new RemoteConfiguration());
        Assert.assertTrue(config.getRequest_headers_map().isEmpty());
    }

    @Test
    public void getDefaultHarvestConfiguration() {
        Assert.assertEquals(new HarvestConfiguration(), HarvestConfiguration.getDefaultHarvestConfiguration());
    }

    @Test
    public void testEquals() {
        HarvestConfiguration thisConfig = Providers.provideHarvestConfiguration();
        HarvestConfiguration thatConfig = Providers.provideHarvestConfiguration();
        Assert.assertTrue(thisConfig.equals(thatConfig));
        Assert.assertFalse(thisConfig.equals(HarvestConfiguration.getDefaultHarvestConfiguration()));
    }

    @Test
    public void testHashCode() {
        HarvestConfiguration thisConfig = Providers.provideHarvestConfiguration();
        HarvestConfiguration thatConfig = Providers.provideHarvestConfiguration();
        Assert.assertTrue(thisConfig.equals(thatConfig));
        Assert.assertNotEquals(thisConfig.hashCode(), thatConfig.hashCode());
    }

    @Test
    public void testGetApplicationId() {
        HarvestConfiguration config = new HarvestConfiguration();
        DataToken dataToken = new DataToken(34, 45);

        config.setData_token(dataToken.asIntArray());
        config.setApplication_id("987");
        Assert.assertEquals("34", config.getApplication_id());

        dataToken.setAccountId(876);
        config.setData_token(dataToken.asIntArray());
        Assert.assertEquals("876", config.getApplication_id());

        dataToken.clear();
        config.setData_token(dataToken.asIntArray());
        Assert.assertEquals(HarvestConfiguration.NO_VALUE, config.getApplication_id());

        String connectResponse = Providers.provideJsonObject("/Connect-Spec-v4.json").toString();
        config = gson.fromJson(connectResponse, HarvestConfiguration.class);
        Assert.assertEquals("1646468", config.getApplication_id());

    }
}