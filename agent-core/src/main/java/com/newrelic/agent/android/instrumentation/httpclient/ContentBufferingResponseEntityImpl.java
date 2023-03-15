/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.httpclient;

import com.newrelic.agent.android.instrumentation.io.CountingInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Deprecated
public class ContentBufferingResponseEntityImpl implements HttpEntity {
	final HttpEntity impl;
	private CountingInputStream contentStream;

	public ContentBufferingResponseEntityImpl(final HttpEntity impl) {
		if (impl == null) {
			throw new IllegalArgumentException("Missing wrapped entity");
		}
		this.impl = impl;
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public void consumeContent() throws IOException {
		impl.consumeContent();
	}

	@Override
	public InputStream getContent() throws IOException, IllegalStateException {
		if (contentStream != null) {
			return contentStream;
		}
		contentStream = new CountingInputStream(impl.getContent(), true);
		return contentStream;
	}

	@Override
	public Header getContentEncoding() {
		return impl.getContentEncoding();
	}

	@Override
	public long getContentLength() {
		return impl.getContentLength();
	}

	@Override
	public Header getContentType() {
		return impl.getContentType();
	}

	@Override
	public boolean isChunked() {
		return impl.isChunked();
	}

	@Override
	public boolean isRepeatable() {
		return impl.isRepeatable();
	}

	@Override
	public boolean isStreaming() {
		return impl.isStreaming();
	}

	@Override
	public void writeTo(OutputStream outputStream) throws IOException {
		impl.writeTo(outputStream);
	}

}
