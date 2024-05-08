/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MessageValidatorTest extends LoggingTests {
    private MessageValidator validator = LogReporting.validator;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void messageValidation() {
        String message = validator.validate((String) null);
        Assert.assertNotNull(message);
        Assert.assertEquals(LogReporting.INVALID_MSG, message);

        message = validator.validate(getRandomMsg(12));
        Assert.assertNotEquals(LogReporting.INVALID_MSG, message);
    }

    @Test
    public void attributeValidation() {
        Map<String, Object> attributes = validator.validate((Map<String, Object>) null);
        Assert.assertNotNull(attributes);
        Assert.assertTrue((attributes.containsKey(LogReporting.LOG_MESSAGE_ATTRIBUTE)));
        Assert.assertTrue((attributes.containsKey(LogReporting.LOG_LEVEL_ATTRIBUTE)));

        attributes = validator.validate(new HashMap<>());
        Assert.assertTrue((attributes.containsKey(LogReporting.LOG_MESSAGE_ATTRIBUTE)));
        Assert.assertTrue((attributes.containsKey(LogReporting.LOG_LEVEL_ATTRIBUTE)));
    }

    @Test
    public void throwableValidation() {
        Throwable throwable = validator.validate((Throwable) null);
        Assert.assertTrue(throwable instanceof IllegalArgumentException);
    }

}