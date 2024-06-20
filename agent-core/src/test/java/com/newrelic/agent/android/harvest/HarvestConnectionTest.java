/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.test.mock.TestTrustManager;
import com.newrelic.agent.android.util.Constants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.HttpURLConnection;
import java.util.HashMap;

@RunWith(JUnit4.class)
public class HarvestConnectionTest {

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

        System.setProperty("http.agent", "JUnit test");
        connection.setServerTimestamp(1234);

        HttpURLConnection post = connection.createPost(uri);
        Assert.assertNotNull(post);
        Assert.assertEquals("POST", post.getRequestMethod());
        Assert.assertEquals(uri, post.getURL().toString());

        Assert.assertNotNull(post.getRequestProperties().containsKey(Constants.Network.APPLICATION_LICENSE_HEADER));
        Assert.assertNotNull(post.getRequestProperties().containsKey(Constants.Network.CONTENT_TYPE_HEADER));

        Assert.assertTrue(post.getRequestProperty(Constants.Network.CONTENT_TYPE_HEADER).equals(Constants.Network.ContentType.JSON));

        Assert.assertEquals(HarvestConnection.CONNECTION_TIMEOUT, post.getConnectTimeout());
        Assert.assertEquals(HarvestConnection.READ_TIMEOUT, post.getReadTimeout());
        Assert.assertTrue(post.getDoInput());
        Assert.assertTrue(post.getDoOutput());
        Assert.assertFalse(post.getUseCaches());
        Assert.assertTrue(post.getRequestProperty(Constants.Network.CONNECT_TIME_HEADER).equals("1234"));
        Assert.assertTrue(post.getRequestProperty(Constants.Network.USER_AGENT_HEADER).equals("JUnit test"));
    }

    @Test
    public void testCreateConnectPost() {
        HttpURLConnection connectPost = connection.createConnectPost();
        Assert.assertNotNull(connectPost);
        Assert.assertEquals("POST", connectPost.getRequestMethod());
        Assert.assertEquals(connection.getCollectorConnectUri(), connectPost.getURL().toString());

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
        Assert.assertEquals(connection.getCollectorDataUri(), dataPost.getURL().toString());
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

        // Check Response Body
        Assert.assertNotEquals(response.getResponseBody().length(), -1);
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

    @Test
    public void testRequestHeaderMap() {
        Assert.assertTrue(connection.requestHeaders.isEmpty());
        connection.setRequestHeaderMap(new HashMap<>() {{
            put("NR-AgentConfiguration", "DEAD-bEef");
            put("NR-Session", "BvHPAMuZETj1AKUN4gkS68oAAAEBLCQoAAAAHQECBAkS6kzIAAA");
        }});
        Assert.assertEquals(2, connection.requestHeaders.size());
        Assert.assertTrue(connection.requestHeaders.containsKey("NR-AgentConfiguration"));
        Assert.assertFalse(connection.requestHeaders.containsKey("nr-session"));

        HttpURLConnection connectPost = connection.createConnectPost();
        Assert.assertNotNull(connectPost);
        Assert.assertNotNull(connectPost.getRequestProperties().containsKey("NR-Session"));
        Assert.assertNotNull(connectPost.getRequestProperties().containsKey("NR-AgentConfiguration"));
    }
}
