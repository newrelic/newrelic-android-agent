/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.util;

import java.util.Map;

public interface ClassAnnotation {
    String getClassName();

    String getName();

    Map<String, Object> getAttributes();
}
