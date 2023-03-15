/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.retrofit;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.concurrent.Executor;

import retrofit.Endpoint;
import retrofit.ErrorHandler;
import retrofit.Profiler;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.Converter;

public class RestAdapterBuilderExtension extends RestAdapter.Builder {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private RestAdapter.Builder impl;

    public RestAdapterBuilderExtension(RestAdapter.Builder impl) {
        this.impl = impl;
    }

    @Override
    public RestAdapter.Builder setEndpoint(String endpoint) {
        return impl.setEndpoint(endpoint);
    }

    @Override
    public RestAdapter.Builder setEndpoint(Endpoint endpoint) {
        return impl.setEndpoint(endpoint);
    }

    @Override
    public RestAdapter.Builder setClient(Client client) {
        log.debug("RestAdapterBuilderExtension.setClient() wrapping client " + client + " with new ClientExtension.");
        return impl.setClient(new ClientExtension(client));
    }

    @Override
    public RestAdapter.Builder setClient(Client.Provider clientProvider) {
        return impl.setClient(clientProvider);
    }

    @Override
    public RestAdapter.Builder setExecutors(Executor httpExecutor, Executor callbackExecutor) {
        return impl.setExecutors(httpExecutor, callbackExecutor);
    }

    @Override
    public RestAdapter.Builder setRequestInterceptor(RequestInterceptor requestInterceptor) {
        return impl.setRequestInterceptor(requestInterceptor);
    }

    @Override
    public RestAdapter.Builder setConverter(Converter converter) {
        return impl.setConverter(converter);
    }

    @Override
    public RestAdapter.Builder setProfiler(Profiler profiler) {
        return impl.setProfiler(profiler);
    }

    @Override
    public RestAdapter.Builder setErrorHandler(ErrorHandler errorHandler) {
        return impl.setErrorHandler(errorHandler);
    }

    @Override
    public RestAdapter.Builder setLog(RestAdapter.Log log) {
        return impl.setLog(log);
    }

    @Override
    public RestAdapter.Builder setLogLevel(RestAdapter.LogLevel logLevel) {
        return impl.setLogLevel(logLevel);
    }

    @Override
    public RestAdapter build() {
        return impl.build();
    }
}
