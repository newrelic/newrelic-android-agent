/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.internal;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
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

    private static RippleDrawable ripple(Drawable content, Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(Color.GRAY), content, mask);
    }

    @Test
    public void getBackgroundColor_rippleDrawable_usesContentNotMask() {
        // RippleDrawable(color, content, mask) lays out [content, mask]; the mask is only a shape
        // and must not be mistaken for the background (a white mask used to be reported as #ffffff).
        View view = new View(context);
        view.setBackground(ripple(new ColorDrawable(Color.RED), new ColorDrawable(Color.WHITE)));
        assertEquals("#ff0000", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_rippleDrawableWithoutMask_usesContent() {
        View view = new View(context);
        view.setBackground(ripple(new ColorDrawable(Color.RED), null));
        assertEquals("#ff0000", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_rippleDrawableWithFilllessMask_usesContent() {
        // The mask has no fill at all, so reading it yields no color.
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.BLUE);
        View view = new View(context);
        view.setBackground(ripple(content, new GradientDrawable()));
        assertEquals("#0000ff", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_materialStyleButton_returnsButtonFill() {
        // Same structure MAUI's MauiMaterialButton produces:
        // Ripple -> [Inset -> Layer -> [Gradient(fill), Gradient(no fill)], Gradient(no fill) mask]
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xFF512BD4);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{fill, new GradientDrawable()});
        InsetDrawable inset = new InsetDrawable(layers, 0);
        View view = new View(context);
        view.setBackground(ripple(inset, new GradientDrawable()));
        assertEquals("#512bd4", ViewBackgroundHelper.getBackgroundColor(view));
    }

    @Test
    public void getBackgroundColor_rippleDrawableWithTransparentContent_returnsEmpty() {
        View view = new View(context);
        view.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT), new ColorDrawable(Color.WHITE)));
        assertEquals("", ViewBackgroundHelper.getBackgroundColor(view));
    }
}
