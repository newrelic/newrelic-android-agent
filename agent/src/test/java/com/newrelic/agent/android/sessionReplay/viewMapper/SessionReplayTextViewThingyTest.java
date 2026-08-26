/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for the defensive NPE fix in {@link SessionReplayTextViewThingy#generateTextCss}.
 *
 * The fix wraps CSS generation in a try-catch and forces Locale.US for numeric formatting
 * so the per-frame replay processor cannot crash from a libcore-level append failure or a
 * comma-decimal locale.
 */
@RunWith(RobolectricTestRunner.class)
public class SessionReplayTextViewThingyTest {

    private Context context;
    private AgentConfiguration agentConfiguration;
    private Locale originalLocale;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        agentConfiguration = new AgentConfiguration();
        // Disable masking so labelText reflects the raw text and tests are deterministic.
        SessionReplayConfiguration cfg = agentConfiguration.getSessionReplayConfiguration();
        cfg.setMaskApplicationText(false);
        cfg.setMaskUserInputText(false);
        originalLocale = Locale.getDefault();
    }

    @org.junit.After
    public void tearDown() {
        Locale.setDefault(originalLocale);
    }

    private TextView newTextView() {
        TextView tv = new TextView(context);
        tv.setText("hello");
        tv.setTextSize(14f);
        tv.layout(0, 0, 100, 50);
        return tv;
    }

    // ==================== generateInlineCss ====================

    @Test
    public void generateInlineCss_basicTextView_returnsCssWithAllExpectedProperties() {
        TextView tv = newTextView();
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertNotNull(css);
        Assert.assertTrue("missing font-size: " + css, css.contains("font-size:"));
        Assert.assertTrue("missing color: " + css, css.contains("color: #"));
        Assert.assertTrue("missing text-align: " + css, css.contains("text-align:"));
        Assert.assertTrue("missing white-space: " + css, css.contains("white-space:"));
    }

    @Test
    public void generateInlineCss_underFrenchLocale_emitsDotDecimalSeparator() {
        // Pre-fix code used String.format(...) without a Locale, so Locale.FRANCE
        // would format 14.00 as "14,00" — invalid CSS. The fix pins Locale.US.
        Locale.setDefault(Locale.FRANCE);
        TextView tv = newTextView();
        tv.setTextSize(14f);
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertFalse("CSS must not contain comma-decimal: " + css, css.contains(","));
        Assert.assertTrue("CSS must use dot-decimal: " + css, css.matches(".*font-size:\\s*\\d+\\.\\d{2}px;.*"));
    }

    @Test
    public void generateInlineCss_doesNotThrow_withNullTypeface() {
        TextView tv = newTextView();
        tv.setTypeface(null);
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertNotNull(css);
        Assert.assertTrue(css.contains("font-size:"));
    }

    @Test
    public void generateInlineCss_doesNotThrow_withEmptyText() {
        TextView tv = newTextView();
        tv.setText("");
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertNotNull(css);
    }

    @Test
    public void generateInlineCss_withCenterGravity_emitsCenterAlignment() {
        TextView tv = newTextView();
        tv.setGravity(Gravity.CENTER);
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertTrue("expected center alignment: " + css, css.contains("text-align: center"));
    }

    @Test
    public void generateInlineCss_withMonospaceTypeface_emitsMonospaceFamily() {
        TextView tv = newTextView();
        tv.setTypeface(Typeface.MONOSPACE);
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertTrue("expected monospace family: " + css, css.contains("font-family: monospace"));
    }

    @Test
    public void generateInlineCss_withCustomTextColor_emitsHexColor() {
        TextView tv = newTextView();
        tv.setTextColor(Color.RED);
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertTrue("expected color: " + css, css.matches(".*color:\\s*#[0-9a-fA-F]{6,8}.*"));
    }

    // ==================== generateCssDescription ====================

    @Test
    public void generateCssDescription_basicTextView_returnsCssRule() {
        TextView tv = newTextView();
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateCssDescription();

        Assert.assertNotNull(css);
        Assert.assertTrue("expected selector prefix: " + css, css.contains("#"));
        Assert.assertTrue("expected rule open: " + css, css.contains("{"));
        Assert.assertTrue("expected font-size: " + css, css.contains("font-size:"));
    }

    // ==================== generateAdditionNodes uses generateInlineCss ====================

    @Test
    public void generateAdditionNodes_doesNotThrow() {
        TextView tv = newTextView();
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        Assert.assertNotNull(thingy.generateAdditionNodes(0));
    }

    // ==================== EditText / password fields ====================

    @Test
    public void generateInlineCss_passwordEditText_doesNotThrow() {
        EditText et = new EditText(context);
        et.setText("secret");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setTextSize(14f);
        et.layout(0, 0, 100, 50);

        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(et), et, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertNotNull(css);
        Assert.assertTrue(css.contains("font-size:"));
    }

    // ==================== generateTextCss defensive paths ====================

    @Test
    public void generateTextCss_withNullCssBuilder_doesNotThrow() throws Exception {
        TextView tv = newTextView();
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        Method m = SessionReplayTextViewThingy.class
                .getDeclaredMethod("generateTextCss", StringBuilder.class);
        m.setAccessible(true);

        // Must early-return on null without throwing.
        m.invoke(thingy, (StringBuilder) null);
    }

    @Test
    public void generateTextCss_appendsExpectedFragmentsToProvidedBuilder() throws Exception {
        TextView tv = newTextView();
        tv.setTextColor(Color.BLUE);
        tv.setGravity(Gravity.CENTER);
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        Method m = SessionReplayTextViewThingy.class
                .getDeclaredMethod("generateTextCss", StringBuilder.class);
        m.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        m.invoke(thingy, sb);
        String css = sb.toString();

        Assert.assertTrue("font-size missing: " + css, css.contains("font-size:"));
        Assert.assertTrue("color missing: " + css, css.contains("color: #"));
        Assert.assertTrue("alignment missing: " + css, css.contains("text-align: center"));
        Assert.assertTrue("white-space missing: " + css, css.contains("white-space:"));
    }

    // ==================== Double-semicolon (;;) regression tests ====================

    @Test
    public void generateInlineCss_neverContainsDoubleSemicolon() {
        // Pre-fix the fontFamily declaration ended in ";" and the code appended an
        // additional "; ", producing "font-family: monospace;; ".
        TextView tv = newTextView();
        tv.setTypeface(Typeface.MONOSPACE);
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateInlineCss();

        Assert.assertFalse("CSS must not contain ';;': " + css, css.contains(";;"));
    }

    @Test
    public void generateCssDescription_neverContainsDoubleSemicolon() {
        TextView tv = newTextView();
        tv.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC));
        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(
                new ViewDetails(tv), tv, agentConfiguration);

        String css = thingy.generateCssDescription();

        Assert.assertFalse("CSS must not contain ';;': " + css, css.contains(";;"));
    }

    @Test
    public void generateDifferences_fontFamilyChange_storesValueOnly_notFullDeclaration() {
        // Pre-fix the metadata stored the full "font-family: monospace;" declaration as
        // the value, which the player serialized as "font-family: font-family: monospace;;".
        TextView tvA = newTextView();
        tvA.setTypeface(Typeface.SERIF);
        SessionReplayTextViewThingy a = new SessionReplayTextViewThingy(
                new ViewDetails(tvA), tvA, agentConfiguration);

        TextView tvB = newTextView();
        tvB.setTypeface(Typeface.MONOSPACE);
        SessionReplayTextViewThingy b = new SessionReplayTextViewThingy(
                new ViewDetails(tvB), tvB, agentConfiguration);

        List<MutationRecord> diffs = a.generateDifferences(b);

        Assert.assertFalse(diffs.isEmpty());
        RRWebMutationData.AttributeRecord attr = (RRWebMutationData.AttributeRecord) diffs.get(0);
        String fontFamily = attr.attributes.getMetadata().get("font-family");
        Assert.assertNotNull("font-family entry missing", fontFamily);
        Assert.assertEquals("font-family value should be just 'monospace'",
                "monospace", fontFamily);
        Assert.assertFalse("must not include 'font-family:' prefix: " + fontFamily,
                fontFamily.contains("font-family:"));
        Assert.assertFalse("must not include trailing ';': " + fontFamily,
                fontFamily.contains(";"));
    }

    @Test
    public void generateDifferences_fontSize_underFrenchLocale_emitsDotDecimal() {
        Locale.setDefault(Locale.FRANCE);
        TextView tvA = newTextView();
        tvA.setTextSize(14f);
        SessionReplayTextViewThingy a = new SessionReplayTextViewThingy(
                new ViewDetails(tvA), tvA, agentConfiguration);

        TextView tvB = newTextView();
        tvB.setTextSize(20f);
        SessionReplayTextViewThingy b = new SessionReplayTextViewThingy(
                new ViewDetails(tvB), tvB, agentConfiguration);

        List<MutationRecord> diffs = a.generateDifferences(b);

        Assert.assertFalse(diffs.isEmpty());
        RRWebMutationData.AttributeRecord attr = (RRWebMutationData.AttributeRecord) diffs.get(0);
        String fontSize = attr.attributes.getMetadata().get("font-size");
        Assert.assertNotNull("font-size entry missing", fontSize);
        Assert.assertFalse("must not contain comma decimal: " + fontSize, fontSize.contains(","));
        Assert.assertTrue("must use dot decimal: " + fontSize, fontSize.matches("\\d+\\.\\d{2}px"));
    }

    @Test
    public void extractFontFamilyValue_returnsValueFromDeclaration() throws Exception {
        Method m = SessionReplayTextViewThingy.class
                .getDeclaredMethod("extractFontFamilyValue", String.class);
        m.setAccessible(true);

        Assert.assertEquals("monospace",
                m.invoke(null, "font-family: monospace;"));
        Assert.assertEquals("monospace",
                m.invoke(null, "font-family: monospace; font-weight: bold;"));
        Assert.assertEquals("sans-serif",
                m.invoke(null, "font-family: sans-serif;"));
        // No font-family declaration (e.g. typeface == null path) → null.
        Assert.assertNull(m.invoke(null, "font-weight: normal; font-style: normal;"));
        Assert.assertNull(m.invoke(null, (Object) null));
    }
}