/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import org.junit.Assert;
import org.junit.Test;

public class TextMaskingStrategyTest {

    @Test
    public void testEnumValues() {
        TextMaskingStrategy[] values = TextMaskingStrategy.values();
        Assert.assertNotNull(values);
        Assert.assertEquals(3, values.length);
    }

    @Test
    public void testMaskAllTextValue() {
        TextMaskingStrategy strategy = TextMaskingStrategy.MASK_ALL_TEXT;
        Assert.assertNotNull(strategy);
        Assert.assertEquals("MASK_ALL_TEXT", strategy.name());
    }

    @Test
    public void testMaskUserInputTextValue() {
        TextMaskingStrategy strategy = TextMaskingStrategy.MASK_USER_INPUT_TEXT;
        Assert.assertNotNull(strategy);
        Assert.assertEquals("MASK_USER_INPUT_TEXT", strategy.name());
    }

    @Test
    public void testMaskNoTextValue() {
        TextMaskingStrategy strategy = TextMaskingStrategy.MASK_NO_TEXT;
        Assert.assertNotNull(strategy);
        Assert.assertEquals("MASK_NO_TEXT", strategy.name());
    }

    @Test
    public void testValueOfMaskAllText() {
        TextMaskingStrategy strategy = TextMaskingStrategy.valueOf("MASK_ALL_TEXT");
        Assert.assertNotNull(strategy);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, strategy);
    }

    @Test
    public void testValueOfMaskUserInputText() {
        TextMaskingStrategy strategy = TextMaskingStrategy.valueOf("MASK_USER_INPUT_TEXT");
        Assert.assertNotNull(strategy);
        Assert.assertEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, strategy);
    }

    @Test
    public void testValueOfMaskNoText() {
        TextMaskingStrategy strategy = TextMaskingStrategy.valueOf("MASK_NO_TEXT");
        Assert.assertNotNull(strategy);
        Assert.assertEquals(TextMaskingStrategy.MASK_NO_TEXT, strategy);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValueOfInvalidValue() {
        TextMaskingStrategy.valueOf("INVALID_STRATEGY");
    }


    @Test(expected = IllegalArgumentException.class)
    public void testValueOfEmpty() {
        TextMaskingStrategy.valueOf("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValueOfLowercase() {
        TextMaskingStrategy.valueOf("mask_all_text");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValueOfWithWhitespace() {
        TextMaskingStrategy.valueOf(" MASK_ALL_TEXT ");
    }

    @Test
    public void testToString() {
        Assert.assertEquals("MASK_ALL_TEXT", TextMaskingStrategy.MASK_ALL_TEXT.toString());
        Assert.assertEquals("MASK_USER_INPUT_TEXT", TextMaskingStrategy.MASK_USER_INPUT_TEXT.toString());
        Assert.assertEquals("MASK_NO_TEXT", TextMaskingStrategy.MASK_NO_TEXT.toString());
    }

    @Test
    public void testOrdinalValues() {
        Assert.assertEquals(0, TextMaskingStrategy.MASK_ALL_TEXT.ordinal());
        Assert.assertEquals(1, TextMaskingStrategy.MASK_USER_INPUT_TEXT.ordinal());
        Assert.assertEquals(2, TextMaskingStrategy.MASK_NO_TEXT.ordinal());
    }

    @Test
    public void testCompareTo() {
        Assert.assertTrue(TextMaskingStrategy.MASK_ALL_TEXT.compareTo(TextMaskingStrategy.MASK_USER_INPUT_TEXT) < 0);
        Assert.assertTrue(TextMaskingStrategy.MASK_USER_INPUT_TEXT.compareTo(TextMaskingStrategy.MASK_NO_TEXT) < 0);
        Assert.assertTrue(TextMaskingStrategy.MASK_NO_TEXT.compareTo(TextMaskingStrategy.MASK_ALL_TEXT) > 0);
    }

    @Test
    public void testEquals() {
        TextMaskingStrategy strategy1 = TextMaskingStrategy.MASK_ALL_TEXT;
        TextMaskingStrategy strategy2 = TextMaskingStrategy.valueOf("MASK_ALL_TEXT");
        Assert.assertEquals(strategy1, strategy2);
        Assert.assertSame(strategy1, strategy2);
    }

    @Test
    public void testNotEquals() {
        Assert.assertNotEquals(TextMaskingStrategy.MASK_ALL_TEXT, TextMaskingStrategy.MASK_USER_INPUT_TEXT);
        Assert.assertNotEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, TextMaskingStrategy.MASK_NO_TEXT);
        Assert.assertNotEquals(TextMaskingStrategy.MASK_ALL_TEXT, TextMaskingStrategy.MASK_NO_TEXT);
    }

    @Test
    public void testHashCode() {
        TextMaskingStrategy strategy1 = TextMaskingStrategy.MASK_ALL_TEXT;
        TextMaskingStrategy strategy2 = TextMaskingStrategy.valueOf("MASK_ALL_TEXT");
        Assert.assertEquals(strategy1.hashCode(), strategy2.hashCode());
    }

    @Test
    public void testSwitchStatement() {
        // Test that enum can be used in switch statements
        for (TextMaskingStrategy strategy : TextMaskingStrategy.values()) {
            String result = getSwitchResult(strategy);
            Assert.assertNotNull(result);
            Assert.assertFalse(result.isEmpty());
        }
    }

    @Test
    public void testSwitchStatementForEachValue() {
        Assert.assertEquals("all", getSwitchResult(TextMaskingStrategy.MASK_ALL_TEXT));
        Assert.assertEquals("user_input", getSwitchResult(TextMaskingStrategy.MASK_USER_INPUT_TEXT));
        Assert.assertEquals("none", getSwitchResult(TextMaskingStrategy.MASK_NO_TEXT));
    }

    @Test
    public void testValuesArrayIsNotMutable() {
        TextMaskingStrategy[] values1 = TextMaskingStrategy.values();
        TextMaskingStrategy[] values2 = TextMaskingStrategy.values();

        Assert.assertNotSame("values() should return a new array each time", values1, values2);
        Assert.assertEquals(values1.length, values2.length);
    }

    @Test
    public void testEnumConstantOrder() {
        TextMaskingStrategy[] values = TextMaskingStrategy.values();
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, values[0]);
        Assert.assertEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, values[1]);
        Assert.assertEquals(TextMaskingStrategy.MASK_NO_TEXT, values[2]);
    }

    // Helper method for switch statement tests
    private String getSwitchResult(TextMaskingStrategy strategy) {
        switch (strategy) {
            case MASK_ALL_TEXT:
                return "all";
            case MASK_USER_INPUT_TEXT:
                return "user_input";
            case MASK_NO_TEXT:
                return "none";
            default:
                return "unknown";
        }
    }
}