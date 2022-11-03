/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.httpclient;

import com.newrelic.agent.android.instrumentation.ApacheInstrumentation;
import com.newrelic.agent.android.instrumentation.TransactionState;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

import java.io.IOException;

@Deprecated
public final class ResponseHandlerImpl<T> implements ResponseHandler<T> {
	private final ResponseHandler<T> impl;
	private final TransactionState transactionState;
	
	private ResponseHandlerImpl(ResponseHandler<T> impl, TransactionState transactionState) {
		this.impl = impl;
		this.transactionState = transactionState;
	}
	
	@Override
	public T handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
		ApacheInstrumentation.inspectAndInstrument(transactionState, response);
		return impl.handleResponse(response);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> ResponseHandler<? extends T> wrap(ResponseHandler<? extends T> impl, TransactionState transactionState) {
		//
		// XXX can't figure out the correct type param voodoo to use for the constructor here.
		//     Packing it full of sweet, sweet lies for now.
		//
		return new ResponseHandlerImpl(impl, transactionState);
	}
}
