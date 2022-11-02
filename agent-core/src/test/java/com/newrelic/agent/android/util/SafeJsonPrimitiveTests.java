/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.ActivitySighting;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SafeJsonPrimitiveTests {
    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testSafeJsonPrimitive() throws Exception {
        try {
            new JsonPrimitive((Boolean) null);
            Assert.fail("Should thrown NPE");
        } catch (Exception e) {
        }

        try {
            new JsonPrimitive((Number) null);
            Assert.fail("Should thrown NPE");
        } catch (Exception e) {
        }

        try {
            new JsonPrimitive((String) null);
            Assert.fail("Should thrown NPE");
        } catch (Exception e) {
        }

        try {
            new JsonPrimitive((Character) null);
            Assert.fail("Should thrown NPE");
        } catch (Exception e) {
        }

        try {
            JsonPrimitive json = SafeJsonPrimitive.factory((Boolean) null);
            Assert.assertEquals("Boolean null should return false", SafeJsonPrimitive.NULL_BOOL, json.getAsBoolean());
            json = SafeJsonPrimitive.factory((Number) null);
            Assert.assertEquals("Number null should return NaN", SafeJsonPrimitive.NULL_NUMBER, json.getAsFloat());
            json = SafeJsonPrimitive.factory((String) null);
            Assert.assertEquals("String null should return 'null'", SafeJsonPrimitive.NULL_STRING, json.getAsString());
            json = SafeJsonPrimitive.factory((Character) null);
            Assert.assertEquals("Character null should return false", SafeJsonPrimitive.NULL_CHAR, json.getAsCharacter());
        } catch (Exception e) {
            Assert.fail("Should not throw NPE on null input");
        }
    }

    @Test
    public void testNullableClassMembers() {
        TestHarvestableArray testHarvestableArray = new TestHarvestableArray();
        JsonArray json = testHarvestableArray.asJsonArray();
        try {
            testHarvestableArray.asJsonArrayWillThrowNPE();
            Assert.fail("Should throw NPE");
        } catch (Exception e) {
        }
        Assert.assertEquals("Should print 'null' as value", "[\"null\",NaN,false,NaN]", json.toString());
        Assert.assertEquals("Should print 'null' as value", "DataToken{nullableString=null, nullableInt=null, nullableBool=null, nullableNumber=null}",
                testHarvestableArray.toString());
    }

    @Test
    public void testNullableMethodArgs() throws Exception {
        ActivitySighting activitySighting = new ActivitySighting(0, "name");
        Assert.assertNotNull("Name should not be null", activitySighting.getName());
        activitySighting.setName(null);
        Assert.assertNull("Name should  be null", activitySighting.getName());
        JsonArray json = activitySighting.asJsonArray();
        Assert.assertEquals("Should print 'null' as value", "[\"null\",0,0]", json.toString());

        HttpTransaction httpTransaction = Providers.provideHttpTransaction();
        httpTransaction.setUrl(null);
        httpTransaction.setCarrier(null);
        httpTransaction.setWanType(null);
        httpTransaction.setHttpMethod(null);

        json = httpTransaction.asJsonArray();
        Assert.assertEquals("Should print 'null' as value", "null", json.get(0).getAsString());
        Assert.assertEquals("Should print 'null' as value", "null", json.get(1).getAsString());
        Assert.assertEquals("Should print 'null' as value", "null", json.get(8).getAsString());
        Assert.assertEquals("Should print 'null' as value", "null", json.get(9).getAsString());
    }


    private class TestHarvestableArray extends HarvestableArray {
        public String nullableString = null;
        public Integer nullableInt = null;
        public Boolean nullableBool = null;
        public Number nullableNumber = null;

        public TestHarvestableArray() {
            super();
        }

        @Override
        public JsonArray asJsonArray() {
            JsonArray array = new JsonArray();
            array.add(SafeJsonPrimitive.factory(nullableString));
            array.add(SafeJsonPrimitive.factory(nullableInt));
            array.add(SafeJsonPrimitive.factory(nullableBool));
            array.add(SafeJsonPrimitive.factory(nullableNumber));
            return array;
        }

        @Override
        public String toString() {
            return "DataToken{" +
                    "nullableString=" + nullableString +
                    ", nullableInt=" + nullableInt +
                    ", nullableBool=" + nullableBool +
                    ", nullableNumber=" + nullableNumber +
                    '}';
        }

        public JsonArray asJsonArrayWillThrowNPE() {
            // JsonPrimitive doesn't like null input
            JsonArray array = new JsonArray();
            array.add(new JsonPrimitive(nullableString));
            return array;
        }
    }

}

