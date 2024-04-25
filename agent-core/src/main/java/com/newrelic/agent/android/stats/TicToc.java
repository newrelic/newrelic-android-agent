/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stats;

public class TicToc {
    private static enum State {
        STOPPED,
        STARTED,
    }

    private long startTime;
    private long endTime;
    private State state;

    public void tic() {
        state = State.STARTED;
        startTime = System.currentTimeMillis();
    }

    public long toc() {
        endTime = System.currentTimeMillis();

        if (state == State.STARTED) {
            state = State.STOPPED;
            return endTime - startTime;
        } else {
            return -1;
        }
    }

    public long peek() {
        return (state == State.STARTED) ? System.currentTimeMillis() - startTime : 0;
    }

}
