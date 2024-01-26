/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;

import java.io.File;

public class LogForwarding {
    static final String LOG_REPORTS_DIR = "newrelic/logreporting";      // root dir for local data files
    static final String LOG_FILE_MASK = "logdata%s%s";                  // log data file name. suffix will indicate working state

    static File logDataStore = new File("").getAbsoluteFile();

    public static void initialize(File rootDir, AgentConfiguration agentConfiguration) {
        logDataStore = new File(rootDir, LOG_REPORTS_DIR);
    }
}
