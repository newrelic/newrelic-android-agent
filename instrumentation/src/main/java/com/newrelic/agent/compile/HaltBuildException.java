/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

public class HaltBuildException extends RuntimeException {
    public HaltBuildException(String message) {
        super(message);
    }

    public HaltBuildException(Exception e) {
        super(e);
    }

    public HaltBuildException(String message, Exception e) {
        super(message, e);
    }
}
