/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DataTokenTest {
    private DataToken dataToken;

    @Before
    public void setUp() throws Exception {
        dataToken = new DataToken(1, 2);
    }

    @Test
    public void testIsValid() {
        Assert.assertTrue(dataToken.isValid());
        dataToken.clear();
        Assert.assertFalse(dataToken.isValid());
    }

    @Test
    public void testValues() {
        Assert.assertEquals(1, dataToken.getAccountId());
        Assert.assertEquals(2, dataToken.getAgentId());
    }

    @Test
    public void testClear() {
        Assert.assertEquals(1, dataToken.getAccountId());
        Assert.assertEquals(2, dataToken.getAgentId());

        dataToken.clear();
        Assert.assertFalse(dataToken.isValid());
        Assert.assertEquals(0, dataToken.getAccountId());
        Assert.assertEquals(0, dataToken.getAgentId());
    }

    @Test
    public void testToJson() {
        Assert.assertEquals("[1,2]", dataToken.toJsonString());
    }

    @Test
    public void testAsIntArray() {
        Assert.assertEquals(2, dataToken.asIntArray().length);
        Assert.assertEquals(1, dataToken.asIntArray()[0]);
        Assert.assertEquals(2, dataToken.asIntArray()[1]);
        try {
            int i = dataToken.asIntArray()[2];
            Assert.fail();
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testAsJsonArray() {
        JsonArray jsonArray = dataToken.asJsonArray();
        Assert.assertEquals(2, jsonArray.size());
        Assert.assertEquals(1, jsonArray.get(0).getAsInt());
        Assert.assertEquals(2, jsonArray.get(1).getAsInt());

        try {
            JsonElement i = jsonArray.get(2);
            Assert.fail();
        } catch (Exception ignored) {
        }
    }
}