/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import org.junit.Assert;
import org.junit.Test;

public class SessionReplayModeTest {

    @Test
    public void testEnumValues() {
        SessionReplayMode[] modes = SessionReplayMode.values();
        Assert.assertEquals(3, modes.length);
        Assert.assertEquals(SessionReplayMode.OFF, modes[0]);
        Assert.assertEquals(SessionReplayMode.ERROR, modes[1]);
        Assert.assertEquals(SessionReplayMode.FULL, modes[2]);
    }

    @Test
    public void testGetValue() {
        Assert.assertEquals("off", SessionReplayMode.OFF.getValue());
        Assert.assertEquals("error", SessionReplayMode.ERROR.getValue());
        Assert.assertEquals("full", SessionReplayMode.FULL.getValue());
    }

    @Test
    public void testToString() {
        Assert.assertEquals("off", SessionReplayMode.OFF.toString());
        Assert.assertEquals("error", SessionReplayMode.ERROR.toString());
        Assert.assertEquals("full", SessionReplayMode.FULL.toString());
    }

    @Test
    public void testFromStringWithValidValues() {
        Assert.assertEquals(SessionReplayMode.OFF, SessionReplayMode.fromString("off"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("error"));
        Assert.assertEquals(SessionReplayMode.FULL, SessionReplayMode.fromString("full"));
    }

    @Test
    public void testFromStringWithCaseInsensitiveValues() {
        Assert.assertEquals(SessionReplayMode.OFF, SessionReplayMode.fromString("OFF"));
        Assert.assertEquals(SessionReplayMode.OFF, SessionReplayMode.fromString("Off"));
        Assert.assertEquals(SessionReplayMode.OFF, SessionReplayMode.fromString("oFf"));

        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("ERROR"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("Error"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("ErRoR"));

        Assert.assertEquals(SessionReplayMode.FULL, SessionReplayMode.fromString("FULL"));
        Assert.assertEquals(SessionReplayMode.FULL, SessionReplayMode.fromString("Full"));
        Assert.assertEquals(SessionReplayMode.FULL, SessionReplayMode.fromString("FuLl"));
    }

    @Test
    public void testFromStringWithNull() {
        // Should default to ERROR mode when null
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString(null));
    }

    @Test
    public void testFromStringWithInvalidValue() {
        // Should default to ERROR mode when unknown value
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("invalid"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("unknown"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString(""));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("   "));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("on"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("disabled"));
    }

    @Test
    public void testFromStringWithWhitespace() {
        // Should handle whitespace in values
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString(" off"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("off "));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString(" error "));
    }

    @Test
    public void testFromStringWithSpecialCharacters() {
        // Should default to ERROR for special characters
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("off!"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("error@"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("full#"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("off-mode"));
    }

    @Test
    public void testFromStringEdgeCases() {
        // Test various edge cases
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("0"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("1"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("true"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.fromString("false"));
    }

    @Test
    public void testEnumEquality() {
        Assert.assertEquals(SessionReplayMode.OFF, SessionReplayMode.valueOf("OFF"));
        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplayMode.valueOf("ERROR"));
        Assert.assertEquals(SessionReplayMode.FULL, SessionReplayMode.valueOf("FULL"));
    }
}