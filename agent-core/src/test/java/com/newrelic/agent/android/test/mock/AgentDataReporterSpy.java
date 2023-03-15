/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.test.mock;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.agentdata.AgentDataReporter;
import com.newrelic.agent.android.payload.Payload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class AgentDataReporterSpy extends AgentDataReporter {

    public AgentDataReporterSpy(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
    }

    public static AgentDataReporter initialize(AgentConfiguration agentConfiguration) {
        instance.set(spy(AgentDataReporter.initialize(agentConfiguration)));

        doReturn(new TestFuture()).when(instance.get()).reportAgentData(any(Payload.class));

        return instance.get();
    }

}
