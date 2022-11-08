/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.payload.PayloadStore;

import java.util.List;

public interface AnalyticsAttributeStore extends PayloadStore<AnalyticsAttribute> {

    /**
     * Store an attribute
     *
     * @param attribute the attribute
     * @return true if the operations succeeded, false otherwise
     */
    boolean store(AnalyticsAttribute attribute);

    /**
     * Fetch all stored attributes
     *
     * @return A List containing all persistent attributes
     */
    List<AnalyticsAttribute> fetchAll();

    /**
     * Returns the count of stored persistent attributes
     *
     * @return the count of stored persistent attributes
     */
    public int count();

    /**
     * Clears all stored persistent attributes
     */
    public void clear();

    /**
     * Delete a specific stored persistent attribute
     *
     * @param attribute the attribute to delete
     */
    public void delete(AnalyticsAttribute attribute);
}
