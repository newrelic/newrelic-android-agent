/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Random;

public class Util {
    private static final Random random = new SecureRandom();

    public static String sanitizeUrl(String urlString) {
        if (urlString == null) {
            return null;
        }

        final URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            return null;
        }

        final StringBuffer sanitizedUrl = new StringBuffer();

        sanitizedUrl.append(url.getProtocol());
        sanitizedUrl.append("://");
        sanitizedUrl.append(url.getHost());
        if (url.getPort() != -1) {
            sanitizedUrl.append(":");
            sanitizedUrl.append(url.getPort());
        }
        sanitizedUrl.append(url.getPath());

        return sanitizedUrl.toString();
    }

    public static Random getRandom() {
        return random;
    }
}
