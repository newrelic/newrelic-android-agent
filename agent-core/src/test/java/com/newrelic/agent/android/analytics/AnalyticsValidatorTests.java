/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class AnalyticsValidatorTests {

    protected static AnalyticsValidator validator;
    protected static Set<AnalyticsAttribute> attributeSet;
    protected JsonObject attributeObject;
    protected String superLongKeyName = "superLongName";
    protected String superLongAttrValue = "superLongValue:";

    @BeforeClass
    public static void setUpClass() throws Exception {
        validator = new AnalyticsValidator();
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

        while (superLongKeyName.length() < AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH) {
            superLongKeyName = superLongKeyName + "0.........9";
        }

        while (superLongAttrValue.length() < AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH) {
            superLongAttrValue = superLongAttrValue + "0.........9";
        }
    }

    @Test
    public void isKeyNameValid() {
        Assert.assertFalse(validator.isValidKeyName(null));
        Assert.assertFalse(validator.isValidKeyName(""));
        Assert.assertTrue(validator.isValidKeyName(".nrrrrr"));
        Assert.assertTrue(validator.isValidKeyName("newrelic."));
        Assert.assertFalse(validator.isValidKeyName(superLongKeyName));
    }

    @Test
    public void isAttributeNameValid() {
        Assert.assertFalse(validator.isValidAttributeName(null));
        Assert.assertFalse(validator.isValidAttributeName(""));
        Assert.assertTrue(validator.isValidAttributeName(".nrrrr"));
        Assert.assertFalse(validator.isValidAttributeName("nr..rrrr"));
        Assert.assertTrue(validator.isValidAttributeName(".newrelic."));
        Assert.assertTrue(validator.isValidAttributeName("newrelic."));
        Assert.assertFalse(validator.isValidAttributeName("newRelic."));
        Assert.assertTrue(validator.isValidAttributeName("Public.facing"));
        Assert.assertFalse(validator.isValidAttributeName("Public_facing"));
    }

    @Test
    public void isAttributeValueValid() {
        Assert.assertTrue(validator.isValidAttributeValue("name", "value"));
        Assert.assertFalse(validator.isValidAttributeValue("name", null));
        Assert.assertFalse(validator.isValidAttributeValue("name", ""));
        Assert.assertFalse(validator.isValidAttributeValue("name", superLongAttrValue));
    }

    @Test
    public void isAttributeNameReserved() {
        Assert.assertTrue(validator.isReservedAttributeName("nr..rrrr"));
        Assert.assertFalse(validator.isReservedAttributeName(".newrelic."));
        Assert.assertFalse(validator.isReservedAttributeName("newrelic."));
        Assert.assertTrue(validator.isReservedAttributeName("newRelic."));
    }

    @Test
    public void isAttributeExcluded() {
        Assert.assertFalse(validator.isExcludedAttributeName("nope"));
        Assert.assertFalse(validator.isExcludedAttributeName(null));
        Assert.assertFalse(validator.isExcludedAttributeName(""));
        Assert.assertTrue(validator.isExcludedAttributeName(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE));
        Assert.assertTrue(validator.isExcludedAttributeName(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE));
        Assert.assertTrue(validator.isExcludedAttributeName(AnalyticsAttribute.APP_UPGRADE_ATTRIBUTE));
    }

    @Test
    public void isEventNameValid() {
        Assert.assertTrue(validator.isValidEventName("eventName"));
        Assert.assertFalse(validator.isValidEventName(null));
        Assert.assertFalse(validator.isValidEventName(""));
        Assert.assertFalse(validator.isValidEventName(superLongKeyName));
    }

    @Test
    public void isEventTypeValid() {
        Assert.assertTrue(validator.isValidEventType("Mobile"));
        Assert.assertFalse(validator.isValidEventType("\t\n Mobile"));
        Assert.assertFalse(validator.isValidEventType("Mobile-Request"));
    }

    @Test
    public void isEventTypeReserved() {
        Assert.assertTrue(validator.isReservedEventType(AnalyticsEvent.EVENT_TYPE_MOBILE));
        Assert.assertTrue(validator.isReservedEventType(AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST));
        Assert.assertTrue(validator.isReservedEventType(AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR));
        Assert.assertTrue(validator.isReservedEventType(AnalyticsEvent.EVENT_TYPE_MOBILE_BREADCRUMB));
        Assert.assertTrue(validator.isReservedEventType(AnalyticsEvent.EVENT_TYPE_MOBILE_CRASH));
        Assert.assertTrue(validator.isReservedEventType(AnalyticsEvent.EVENT_TYPE_MOBILE_USER_ACTION));

        Assert.assertFalse(validator.isReservedEventType(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE));
        Assert.assertFalse(validator.isReservedEventType("Mobile-Request"));
    }

    @Test
    public void toValidEventType() {
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE, validator.toValidEventType(null));
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE, validator.toValidEventType(""));
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR, validator.toValidEventType(AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR));
    }

    @Test
    public void toValidCategory() {
        Assert.assertEquals(AnalyticsEventCategory.Custom, validator.toValidCategory(null));
        Assert.assertEquals(AnalyticsEventCategory.Custom, validator.toValidCategory(AnalyticsEventCategory.Custom));
        Assert.assertEquals(AnalyticsEventCategory.Breadcrumb, validator.toValidCategory(AnalyticsEventCategory.Breadcrumb));
    }

    @Test
    public void goldenValidationTest() throws Exception {
        final StringBuilder superLongAttributeName = new StringBuilder("superLongAttributeName");
        while (superLongAttributeName.length() < AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH) {
            superLongAttributeName.append(superLongAttributeName.toString());
        }

        final StringBuilder superLongAttributeValue = new StringBuilder("superLongAttributeValue");
        while (superLongAttributeValue.length() < AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH) {
            superLongAttributeValue.append(superLongAttributeValue.toString());
        }

        Map<String, Object> attributeMap = new HashMap<String, Object>() {{
            put(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE, "reserved");
            put("InvalidNumericType", new AtomicInteger(1));
            put(null, 1);             // should not throw
            put("", "emptyKey");
            put(superLongAttributeName.toString(), "superLongAttributeName");
            put("superLongAttributeValue", superLongAttributeValue.toString());
        }};

        Set<AnalyticsAttribute> filteredResults = validator.toValidatedAnalyticsAttributes(attributeMap);
        Assert.assertTrue("Should remove all invalid attributes from passed map", filteredResults.isEmpty());

        Set<AnalyticsAttribute> attributeSet = new HashSet<AnalyticsAttribute>() {{
            add(new AnalyticsAttribute(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE, "reserved"));
            add(new AnalyticsAttribute("", "emptyKey"));
            add(new AnalyticsAttribute(superLongAttributeName.toString(), "superLongAttributeName"));
            add(new AnalyticsAttribute("superLongAttributeValue", superLongAttributeValue.toString()));
        }};

        filteredResults = validator.toValidatedAnalyticsAttributes(attributeSet);
        Assert.assertTrue("Should remove all invalid attributes from passed set", filteredResults.isEmpty());
    }
}
