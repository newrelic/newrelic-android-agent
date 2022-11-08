/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.stats.StatsEngine;

import java.text.MessageFormat;

public class AgentHealth extends HarvestableArray {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    public static final String DEFAULT_KEY = "Exception";

    protected final AgentHealthExceptions agentHealthExceptions = new AgentHealthExceptions();

    public static void noticeException(Exception exception) {
        AgentHealthException agentHealthException = null;
        if (exception != null) {
            agentHealthException = new AgentHealthException(exception);
        }
        // will flag null args
        noticeException(agentHealthException);

    }

    public static void noticeException(AgentHealthException exception) {
        // will flag null args
        noticeException(exception, DEFAULT_KEY);
    }

    public static void noticeException(AgentHealthException exception, final String key) {
        if (exception != null) {
            final StatsEngine statsEngine = StatsEngine.get();

            if (statsEngine != null) {
                if( key == null ) {
                    log.warning("Passed metric key is null. Defaulting to " + DEFAULT_KEY);
                }

                statsEngine.inc(MessageFormat.format("Supportability/AgentHealth/{0}/{1}/{2}/{3}",
                        (key == null) ? DEFAULT_KEY : key,
                        exception.getSourceClass(),
                        exception.getSourceMethod(),
                        exception.getExceptionClass()));

                TaskQueue.queue(exception);
            } else {
                log.error("StatsEngine is null. Exception not recorded.");
            }
        } else {
            log.error("AgentHealthException is null. StatsEngine not updated");
        }

    }

    public void addException(AgentHealthException exception) {
        agentHealthExceptions.add(exception);
    }

    public void clear() {
        agentHealthExceptions.clear();
    }

    @Override
    public JsonArray asJsonArray() {
        final JsonArray data = new JsonArray();

        if (!agentHealthExceptions.isEmpty()) {
            data.add(agentHealthExceptions.asJsonObject());
        }

        return data;
    }
}
