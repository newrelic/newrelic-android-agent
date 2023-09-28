/*
 * Copyright (c) 2021-2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.InstrumentationAgent;
import com.newrelic.agent.TestContext;

import org.junit.Before;
import org.junit.Test;

public class WrapMethodClassVisitorTest {

    TestContext testContext;

    @Before
    public void setUp() throws Exception {
        testContext = new TestContext();
    }

    @Test
    public void testTryReplaceCallSite() {
        AgentMethodDelegateClassVisitor cv = new AgentMethodDelegateClassVisitor(testContext.classWriter, testContext.instrumentationContext, InstrumentationAgent.LOGGER);
        String targets[] = {
                "INVOKESTATIC com/newrelic/agent/android/instrumentation/okhttp3/OkHttp3Instrumentation.build (Lokhttp3/Request$Builder;)Lokhttp3/Request;",
                "INVOKESTATIC com/newrelic/agent/android/instrumentation/okhttp3/OkHttp3Instrumentation.newCall (Lokhttp3/OkHttpClient;Lokhttp3/Request;)Lokhttp3/Call;"
        };
        testContext.testVisitorInjection("/visitor/replaceCallSite/CancelCall.class", cv, targets);
    }

    @Test
    public void testTryWrapReturnValue() {
        AgentMethodDelegateClassVisitor cv = new AgentMethodDelegateClassVisitor(testContext.classWriter, testContext.instrumentationContext, InstrumentationAgent.LOGGER);
        String targets[] = {
                "INVOKESTATIC com/newrelic/agent/android/instrumentation/URLConnectionInstrumentation.openConnection (Ljava/net/URLConnection;)Ljava/net/URLConnection;",
                "INVOKESTATIC com/newrelic/agent/android/instrumentation/okhttp3/OkHttp3Instrumentation.build (Lokhttp3/Request$Builder;)Lokhttp3/Request;",
        };
        testContext.testVisitorInjection("/visitor/wrapReturn/OkHttpURLConnectionRegression.class", cv, targets);
    }
}