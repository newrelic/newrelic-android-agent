/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TestUtil {
	public static String slurp(final InputStream stream) throws IOException {
		final char[] buf = new char[8192];
		final StringBuilder sb = new StringBuilder();
		final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		for (;;) {
			int n = reader.read(buf);
			if (n < 0) break;
			sb.append(buf, 0, n);
		}
		return sb.toString();
	}
}
