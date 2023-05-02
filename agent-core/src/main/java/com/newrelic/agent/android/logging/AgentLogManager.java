/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import jdk.internal.org.jline.utils.Log;

public class AgentLogManager {
    private static DefaultAgentLog instance = new DefaultAgentLog();

    public static AgentLog getAgentLog() {
        return instance;
    }

    public static void setAgentLog(AgentLog instance) {
        AgentLogManager.instance.setImpl(instance);
    }

    public static void uploadLog() {
        try {
            AgentConfiguration agentConfiguration = new AgentConfiguration();

            JsonArray jsonArr = new JsonArray();
            JsonObject jsonObj = new JsonObject();
            jsonObj.addProperty("message", "test from android agent");
            jsonObj.addProperty("entity.guid", "MjUxOTY2NnxNT0JJTEV8QVBQTElDQVRJT058NTM0NTQ0NTUz");
            jsonObj.addProperty("sessionId", agentConfiguration.getSessionID());
            jsonArr.add(jsonObj);

            URL url = new URL("https://log-api.newrelic.com/log/v1?Api-Key=f57026e8e5904c4a81dd9a267ae30971FFFFNRAL");
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setDoInput(true);

            DataOutputStream localdos = new DataOutputStream(conn.getOutputStream());
            localdos.writeBytes(jsonArr.toString());
            localdos.flush();
            localdos.close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder sb = new StringBuilder();
            while (reader.readLine() != null) {
                sb.append(reader.readLine());
            }
            getAgentLog().info(sb.toString());
		} catch (Exception ex) {
			ex.printStackTrace();
        }
    }
}
