/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

/**
 * Information about a {@link Thread}, including its {@code id} and {@code name}.
 */
public class ThreadInfo {
    private long id;
    private String name;

    public ThreadInfo() {
        this(Thread.currentThread());
    }

    public ThreadInfo(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public ThreadInfo(Thread thread) {
        this(thread.getId(), thread.getName());
    }

    public static ThreadInfo fromThread(Thread thread) {
        return new ThreadInfo(thread);
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "ThreadInfo{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
