/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stats;

public class TicToc {
    public static enum State {
        STOPPED,
        STARTED,
    }

    private long startTime;
    private long endTime;
    private State state;

    public void tic() {
        state = State.STARTED;
        startTime = System.nanoTime();
    }

    public long toc() {
        endTime = System.nanoTime();

        if (state == State.STARTED) {
            state = State.STOPPED;
            return endTime - startTime;
        } else {
            return -1;
        }
    }

    public long peek() {
        return (state == State.STARTED) ? System.nanoTime() - startTime : 0;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

}
