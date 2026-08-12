/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.payload.PayloadStore;

import java.util.List;

public interface AnalyticsEventStore extends PayloadStore<AnalyticsEvent> {

    /**
     * Store an event
     *
     * @param event the event
     * @return true if the operations succeeded, false otherwise
     */
    boolean store(AnalyticsEvent event);

    /**
     * Fetch all stored event
     *
     * @return A List containing all persistent event
     */
    List<AnalyticsEvent> fetchAll();

    /**
     * Returns the count of stored persistent events
     *
     * @return the count of stored persistent events
     */
    public int count();

    /**
     * Clears all stored persistent events
     */
    public void clear();

    /**
     * Delete a specific stored persistent event
     *
     * @param event the event to delete
     */
    public void delete(AnalyticsEvent event);

    /**
     * Block until any writes/deletes queued prior to this call have been applied. Stores that
     * perform I/O synchronously can leave this as a no-op.
     *
     * @param timeoutMs maximum time to wait
     */
    default void flush(long timeoutMs) {
    }
}
