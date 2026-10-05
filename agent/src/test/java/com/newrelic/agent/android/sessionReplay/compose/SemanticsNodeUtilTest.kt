/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticsNodeUtilTest {

    @Test
    fun toHexColor_opaqueColor_omitsAlpha() {
        assertEquals("ff0000", SemanticsNodeUtil.toHexColor(0xFFFF0000.toInt()))
        assertEquals("000000", SemanticsNodeUtil.toHexColor(0xFF000000.toInt()))
    }

    @Test
    fun toHexColor_fullyTransparentColor_returnsTransparent() {
        // NR-625270: Color.Transparent used to be emitted as opaque "FFFFFF"
        assertEquals("transparent", SemanticsNodeUtil.toHexColor(0x00000000))
        assertEquals("transparent", SemanticsNodeUtil.toHexColor(0x00FFFFFF))
    }

    @Test
    fun toHexColor_translucentColor_appendsAlpha() {
        assertEquals("ffffff80", SemanticsNodeUtil.toHexColor(0x80FFFFFF.toInt()))
    }

    @Test
    fun toHexColor_lowAlphaColor_keepsAllChannels() {
        // Integer.toHexString drops leading zeros, which used to shift the red channel off for alpha < 0x10
        assertEquals("1234560f", SemanticsNodeUtil.toHexColor(0x0F123456))
    }

    @Test
    fun toCssColor_hexColor_addsHashPrefix() {
        assertEquals("#ff0000", SemanticsNodeUtil.toCssColor("ff0000"))
        assertEquals("#ffffff80", SemanticsNodeUtil.toCssColor("ffffff80"))
    }

    @Test
    fun toCssColor_missingOrTransparent_returnsTransparent() {
        assertEquals("transparent", SemanticsNodeUtil.toCssColor(null))
        assertEquals("transparent", SemanticsNodeUtil.toCssColor(""))
        assertEquals("transparent", SemanticsNodeUtil.toCssColor("transparent"))
    }
}
