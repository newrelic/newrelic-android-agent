/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.test.spy;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.agentdata.AgentDataReporter;
import com.newrelic.agent.android.payload.Payload;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;


public class AgentDataReporterSpy extends AgentDataReporter {

    public AgentDataReporterSpy(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
    }

    public static AgentDataReporter initialize(AgentConfiguration agentConfiguration) {
        instance.set(spy(AgentDataReporter.initialize(agentConfiguration)));

        doReturn(new TestFuture()).when(instance.get()).storeAndReportAgentData(any(Payload.class));

        return instance.get();
    }

    private static class TestFuture implements Future<Object> {

        @Override
        public boolean cancel(boolean b) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Object get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }

}
