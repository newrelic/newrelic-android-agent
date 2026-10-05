/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.internal;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ViewBackgroundHelperTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void toRGBAHexString_opaqueColor_omitsAlpha() {
        assertEquals("#ff0000", ViewBackgroundHelper.toRGBAHexString(0xFFFF0000));
        assertEquals("#000000", ViewBackgroundHelper.toRGBAHexString(Color.BLACK));
    }

    @Test
    public void toRGBAHexString_fullyTransparentColor_returnsEmpty() {
        assertEquals("", ViewBackgroundHelper.toRGBAHexString(0x00FFFFFF));
        assertEquals("", ViewBackgroundHelper.toRGBAHexString(Color.TRANSPARENT));
    }

    @Test
    public void toRGBAHexString_translucentColor_appendsAlpha() {
        assertEquals("#ffffff80", ViewBackgroundHelper.toRGBAHexString(0x80FFFFFF));
        assertEquals("#12345601", ViewBackgroundHelper.toRGBAHexString(0x01123456));
    }

    @Test
    public void getBackgroundColor_noBackground_returnsEmpty() {
        View view = new View(context);
        assertEquals("", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_opaqueColorDrawable() {
        View view = new View(context);
        view.setBackground(new ColorDrawable(Color.RED));
        assertEquals("#ff0000", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_transparentWhiteColorDrawable_returnsEmpty() {
        // NR-625270: a transparent-white background used to be emitted as opaque #ffffff
        View view = new View(context);
        view.setBackground(new ColorDrawable(0x00FFFFFF));
        assertEquals("", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_translucentColorDrawable_keepsAlpha() {
        View view = new View(context);
        view.setBackground(new ColorDrawable(0x80000000));
        assertEquals("#00000080", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_transparentGradientDrawable_returnsEmpty() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        View view = new View(context);
        view.setBackground(drawable);
        assertEquals("", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_layerDrawable_skipsTransparentLayers() {
        LayerDrawable drawable = new LayerDrawable(new Drawable[]{
                new ColorDrawable(Color.TRANSPARENT),
                new ColorDrawable(Color.BLUE)
        });
        View view = new View(context);
        view.setBackground(drawable);
        assertEquals("#0000ff", ViewBackgroundHelper.getBackgroundColor(view));
    }
}
