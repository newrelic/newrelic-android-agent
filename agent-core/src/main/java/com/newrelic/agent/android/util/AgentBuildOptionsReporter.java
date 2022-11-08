/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import com.newrelic.agent.android.Agent;

public class AgentBuildOptionsReporter {

    /**
     * Prints the value of the build configuration to the console.
     *
     * This is intended to be used post-compilation to determine what options were used to create
     * the build.
     * 
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("Agent version: " + Agent.getVersion());
        System.out.println("Build ID: " + Agent.getBuildId());
    }
}
