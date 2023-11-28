/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class HarvesterTest {
    Harvester harvester;
    String connectResponse = "{" +
            "  \"server_timestamp\": 1697217384," +
            "  \"report_max_transaction_count\": 1000," +
            "  \"report_max_transaction_age\": 600," +
            "  \"stack_trace_limit\": 100," +
            "  \"activity_trace_min_utilization\": 0.3," +
            "  \"response_body_limit\": 2048," +
            "  \"data_report_period\": 60," +
            "  \"activity_trace_max_size\": 65535," +
            "  \"collect_network_errors\": true," +
            "  \"error_limit\": 50," +
            "  \"at_capture\": [ 1, [ ] ]," +
            "  \"data_token\": [ 52088176, 601346929 ]," +
            "  \"cross_process_id\": \"XAUAWFFQGwYCVFlaBgYB\"," +
            "  \"encoding_key\": \"d67afc830dab717fd163bfcb0b8b88423e9a1a3b\"," +
            "  \"account_id\": \"837973\"," +
            "  \"application_id\": \"52088176\"," +
            "  \"trusted_account_key\": \"765705\"," +
            "  \"log_reporting\": {" +
            "        \"entity_guid\": \"a4d39d21-588b-4342-ad87-967243533949\"," +
            "        \"enabled\": true," +
            "        \"level\": VERBOSE," +
            "        \"data_report_period\": 15," +
            "        \"expiration_period\": 3600" +
            "    }" +
            "}";

    @Before
    public void setUp() throws Exception {
        harvester = new Harvester();
    }

    @Test
    public void parseHarvesterConfiguration() {
        HarvestResponse mockedResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(connectResponse).when(mockedResponse).getResponseBody();

        HarvestConfiguration harvestConfig = harvester.parseHarvesterConfiguration(mockedResponse);
        Assert.assertNotNull(harvestConfig);
        Assert.assertNotNull(harvestConfig.getLog_reporting());
    }

}