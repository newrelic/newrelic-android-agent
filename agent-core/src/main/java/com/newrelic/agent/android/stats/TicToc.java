/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stats;

public class TicToc {
    protected static enum State {
        STOPPED,
        STARTED,
    }

    private long startTime;
    private long endTime;
    private State state;

    public TicToc tic() {
        state = State.STARTED;
        startTime = System.nanoTime();
        return this;
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

    protected long getStartTime() {
        return startTime;
    }

    protected void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    protected long getEndTime() {
        return endTime;
    }

    protected void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    protected State getState() {
        return state;
    }

    protected void setState(State state) {
        this.state = state;
    }

}
