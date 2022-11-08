/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class AnalyticsAttributeTests {

    protected static Set<AnalyticsAttribute> attributeSet;
    protected JsonObject attributeObject;

    @BeforeClass
    public static void setUpClass() throws Exception {
        attributeSet = new HashSet<AnalyticsAttribute>();
        attributeSet.add(new AnalyticsAttribute("string", "string"));
        attributeSet.add(new AnalyticsAttribute("double  ", 1523639280334d));
        attributeSet.add(new AnalyticsAttribute("boolean", false));
    }

    @Before
    public void setUp() throws Exception {
        attributeObject = new JsonObject();
        for (AnalyticsAttribute attribute : attributeSet) {
            attributeObject.add(attribute.getName(), attribute.asJsonElement());
        }
    }

    @Test
    public void testNewFromJson() throws Exception {
        Set<AnalyticsAttribute> attributes = AnalyticsAttribute.newFromJson(attributeObject);
        Assert.assertEquals("Should serialize from Json object", attributes.size(), attributeSet.size());
        for (AnalyticsAttribute attr : attributes) {
            JsonPrimitive elem = attributeObject.get(attr.getName()).getAsJsonPrimitive();
            Assert.assertNotNull("Should find attribute in Json", elem);
            if (elem.isString()) {
                Assert.assertTrue("Json element should contain string value", elem.getAsString() == attr.getStringValue());
            } else if (elem.isNumber()) {
                Assert.assertTrue("Json element should contain double value", 1523639280334d == attr.getDoubleValue());
            } else if (elem.isBoolean()) {
                Assert.assertTrue("Json element should contain bool value", elem.getAsBoolean() == attr.getBooleanValue());
            }
        }
    }

    public static AnalyticsAttribute getAttributeByName(Collection<AnalyticsAttribute> attributes, String name) {
        for (AnalyticsAttribute eventAttr : attributes) {
            if (eventAttr.getName().equalsIgnoreCase(name)) {
                return eventAttr;
            }
        }
        return null;
    }

}
