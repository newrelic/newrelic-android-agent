/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import java.io.IOException;
import java.io.InputStream;

public class TestUtil {
	public static String slurp(final InputStream stream) throws IOException {
		return Streams.slurpString(stream);
	}
}
