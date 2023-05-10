/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.activity;

/**
 * An Activity which is defined by a user through the API.
 */
public class NamedActivity extends BaseMeasuredActivity {
    /**
     * Create a new NamedActivity with the given name.
     * @param activityName Activity name
     */
    public NamedActivity(String activityName) {
        super();
        setName(activityName);
        setAutoInstrumented(false);
    }

    public void rename(String activityName) {
        setName(activityName);
    }
}
