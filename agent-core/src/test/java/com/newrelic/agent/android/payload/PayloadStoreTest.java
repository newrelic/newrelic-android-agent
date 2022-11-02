/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class PayloadStoreTest {
    private PayloadStore<Integer> payloadStore;

    @Before
    public void setUp() throws Exception {
        payloadStore = new PayloadStore<Integer>() {
            Set<Integer> payloadStore = new HashSet<>();

            @Override
            public boolean store(Integer data) {
                boolean stored = payloadStore.add(data);
                Assert.assertTrue(payloadStore.contains(data));
                return stored;
            }

            @Override
            public List<Integer> fetchAll() {
                return new ArrayList<>(payloadStore);
            }

            @Override
            public int count() {
                return payloadStore.size();
            }

            @Override
            public void clear() {
                payloadStore.clear();
            }

            @Override
            public void delete(Integer data) {
                payloadStore.remove(data);
                Assert.assertFalse(payloadStore.contains(data));
            }
        };

    }

    @Test
    public void store() throws Exception {
        Assert.assertTrue(payloadStore.store(1));
        Assert.assertFalse(payloadStore.store(1));
        Assert.assertTrue(payloadStore.store(2));
    }

    @Test
    public void fetchAll() throws Exception {
        Assert.assertTrue(payloadStore.fetchAll() instanceof List);
        payloadStore.store(1);
        payloadStore.store(2);
        payloadStore.store(3);
        payloadStore.store(4);
        Assert.assertEquals(payloadStore.fetchAll().size(), 4);
        Assert.assertEquals(payloadStore.count(), 4);
    }

    @Test
    public void count() throws Exception {
        payloadStore.store(1);
        payloadStore.store(1);
        payloadStore.store(1);
        payloadStore.store(1);
        Assert.assertEquals(payloadStore.count(), 1);

        payloadStore.store(2);
        payloadStore.store(3);
        Assert.assertEquals(payloadStore.count(), 3);
    }

    @Test
    public void clear() throws Exception {
        payloadStore.clear();
        Assert.assertTrue(payloadStore.fetchAll().isEmpty());
    }

    @Test
    public void delete() throws Exception {
        payloadStore.store(1);
        payloadStore.store(2);
        payloadStore.store(3);

        payloadStore.delete(2);
        payloadStore.delete(3);

        Assert.assertEquals(payloadStore.count(), 1);
        Assert.assertTrue(payloadStore.fetchAll().contains(1));
        Assert.assertFalse(payloadStore.fetchAll().contains(2));
        Assert.assertFalse(payloadStore.fetchAll().contains(3));
    }

}