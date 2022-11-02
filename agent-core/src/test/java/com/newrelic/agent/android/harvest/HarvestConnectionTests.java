/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.test.mock.TestTrustManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.HttpURLConnection;

@RunWith(JUnit4.class)
public class HarvestConnectionTests {

    private final static String COLLECTOR_HOST = "staging-mobile-collector.newrelic.com";
    private final static String ENABLED_APP_TOKEN_STAGING = "AAa2d4baa1094bf9049bb22895935e46f85c45c211";
    private final static String DISABLED_APP_TOKEN_STAGING = "AA06d1964231f6c881cedeaa44e837bde4079c683d";

    private HarvestConnection connection;

    @BeforeClass
    public static void setUpSsl() {
        TestTrustManager.setUpSocketFactory();
    }

    @Before
    public void setUp() throws Exception {
        connection = new HarvestConnection();
        connection.setApplicationToken("app token");
        connection.setCollectorHost(COLLECTOR_HOST);
    }

    @Test
    public void testCreatePost() {
        final String uri = "https://mobile-collector.newrelic.com/foo";
        HttpURLConnection post = connection.createPost(uri);
        Assert.assertNotNull(post);
        Assert.assertEquals("POST", post.getRequestMethod());
        Assert.assertEquals(uri, post.getURL().toString());
    }

    @Test
    public void testCreateConnectPost() {
        final String uri = "https://staging-mobile-collector.newrelic.com/mobile/v4/connect";
        HttpURLConnection connectPost = connection.createConnectPost();
        Assert.assertNotNull(connectPost);
        Assert.assertEquals("POST", connectPost.getRequestMethod());
        Assert.assertEquals(uri, connectPost.getURL().toString());

        connection.useSsl(true);
        connectPost = connection.createConnectPost();
        Assert.assertNotNull(connectPost);
        Assert.assertTrue(connectPost.getURL().getProtocol().equals("https"));
    }

    @Test
    public void testCreateDataPost() {
        final String uri = "https://staging-mobile-collector.newrelic.com/mobile/v3/data";
        HttpURLConnection dataPost = connection.createDataPost();
        Assert.assertNotNull(dataPost);
        Assert.assertEquals("POST", dataPost.getRequestMethod());
        Assert.assertEquals(uri, dataPost.getURL().toString());

        connection.useSsl(true);
        dataPost = connection.createDataPost();

        Assert.assertNotNull(dataPost);
        Assert.assertTrue(dataPost.getURL().getProtocol().equals("https"));
    }

    @Test
    public void testSend() {
        connection.setServerTimestamp(1234);

        HttpURLConnection connectPost = connection.createConnectPost();
        Assert.assertNotNull(connectPost);

        HarvestResponse response = connection.send(connectPost, "unit tests");
        Assert.assertNotNull(response);
        Assert.assertEquals(401, response.getStatusCode());
        Assert.assertEquals("", response.getResponseBody());

        // Check Response Code
        Assert.assertTrue(response.getResponseCode().isError());
        Assert.assertEquals(HarvestResponse.Code.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    public void testSendConnect() {
        connection.setServerTimestamp(1234);
        connection.setConnectInformation(new ConnectInformation(Agent.getApplicationInformation(), Agent.getDeviceInformation()));

        HarvestResponse response = connection.sendConnect();
        Assert.assertNotNull(response);
        Assert.assertEquals(401, response.getStatusCode());
        Assert.assertEquals("", response.getResponseBody());

        // Check Response Code
        Assert.assertTrue(response.getResponseCode().isError());
        Assert.assertEquals(HarvestResponse.Code.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    public void testSendData() {
        connection.setServerTimestamp(1234);

        // Sending connect info as data instead on purpose..to generate a 403.
        HarvestResponse response = connection.sendData(new ConnectInformation(Agent.getApplicationInformation(), Agent.getDeviceInformation()));
        Assert.assertNotNull(response);
        Assert.assertEquals(403, response.getStatusCode());
        Assert.assertTrue(response.getResponseBody().contains(""));

        // Check Response Code
        Assert.assertTrue(response.getResponseCode().isError());
        Assert.assertEquals(HarvestResponse.Code.FORBIDDEN, response.getResponseCode());
    }

    @Test
    public void testSendDisabledAppToken() {
        // Can't use staging collector
        connection.setCollectorHost("mobile-collector.newrelic.com");
        connection.setApplicationToken(DISABLED_APP_TOKEN_STAGING);
        connection.useSsl(true);

        HttpURLConnection connectPost = connection.createConnectPost();
        Assert.assertNotNull(connectPost);

        HarvestResponse response = connection.send(connectPost, "unit tests");
        Assert.assertNotNull(response);
        Assert.assertEquals(401, response.getStatusCode());
        Assert.assertEquals("", response.getResponseBody());

        // Check Response Code
        Assert.assertTrue(response.getResponseCode().isError());
        Assert.assertEquals(HarvestResponse.Code.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    public void testSendEnabledAppToken() {
        connection.setConnectInformation(new ConnectInformation(Agent.getApplicationInformation(), Agent.getDeviceInformation()));
        connection.setApplicationToken(ENABLED_APP_TOKEN_STAGING);

        HarvestResponse response = connection.sendConnect();
        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatusCode());
        Assert.assertTrue(response.getResponseBody().contains("data_token"));

        // Check Response Code
        Assert.assertTrue(response.getResponseCode().isOK());
        Assert.assertEquals(HarvestResponse.Code.OK, response.getResponseCode());
    }
}
