/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

public class EnvironmentInformation {
    private long memoryUsage;
    private int orientation;
    private String networkStatus;
    private String networkWanType;
    private long[] diskAvailable;

    public EnvironmentInformation() {}

    public EnvironmentInformation(long memoryUsage, int orientation, String networkStatus,
                                  String networkWanType, long[] diskAvailable) {
        this.memoryUsage = memoryUsage;
        this.orientation = orientation;
        this.networkStatus = networkStatus;
        this.networkWanType = networkWanType;
        this.diskAvailable = diskAvailable;
    }

    public void setMemoryUsage(long memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public void setNetworkStatus(String networkStatus) {
        this.networkStatus = networkStatus;
    }

    public void setNetworkWanType(String networkWanType) {
        this.networkWanType = networkWanType;
    }

    public void setDiskAvailable(long[] diskAvailable) {
        this.diskAvailable = diskAvailable;
    }

    public long getMemoryUsage() {
        return memoryUsage;
    }

    public int getOrientation() {
        return orientation;
    }

    public String getNetworkStatus() {
        return networkStatus;
    }

    public String getNetworkWanType() {
        return networkWanType;
    }

    public long[] getDiskAvailable() {
        return diskAvailable;
    }
}
