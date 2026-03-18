///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.graphics.Color;
//import android.graphics.Typeface;
//import android.text.InputType;
//import android.view.Gravity;
//import android.widget.EditText;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.AgentConfiguration;
//import com.newrelic.agent.android.R;
//import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
//
//import org.junit.After;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayTextViewThingyTest {
//
//    private Context context;
//    private AgentConfiguration agentConfiguration;
//    private SessionReplayConfiguration sessionReplayConfiguration;
//    private SessionReplayLocalConfiguration sessionReplayLocalConfiguration;
//
//    @Before
//    public void setUp() {
//        context = ApplicationProvider.getApplicationContext();
//        agentConfiguration = new AgentConfiguration();
//
//        sessionReplayConfiguration = new SessionReplayConfiguration();
//        sessionReplayConfiguration.setEnabled(true);
//        sessionReplayConfiguration.setMode("full");
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        sessionReplayLocalConfiguration = new SessionReplayLocalConfiguration();
//
//        agentConfiguration.setSessionReplayConfiguration(sessionReplayConfiguration);
//        agentConfiguration.setSessionReplayLocalConfiguration(sessionReplayLocalConfiguration);
//    }
//
//    @After
//    public void tearDown() {
//        sessionReplayLocalConfiguration.clearAllViewMasks();
//    }
//
//    // ==================== BASIC PROPERTY EXTRACTION TESTS ====================
//
//    @Test
//    public void testConstructorWithBasicTextView() {
//        TextView textView = new TextView(context);
//        textView.setText("Hello World");
//        textView.setTextSize(16f);
//        textView.setTextColor(Color.BLACK);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals("Hello World", thingy.getLabelText());
//        Assert.assertTrue(thingy.getFontSize() > 0);
//        Assert.assertNotNull(thingy.getFontFamily());
//        Assert.assertNotNull(thingy.getTextColor());
//        Assert.assertNotNull(thingy.getTextAlign());
//    }
//
//    @Test
//    public void testTextExtractionWithEmptyText() {
//        TextView textView = new TextView(context);
//        textView.setText("");
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("", thingy.getLabelText());
//    }
//
//    @Test
//    public void testTextExtractionWithNullText() {
//        TextView textView = new TextView(context);
//        textView.setText(null);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("", thingy.getLabelText());
//    }
//
//    @Test
//    public void testTextExtractionWithLongText() {
//        TextView textView = new TextView(context);
//        String longText = "This is a very long text that contains many words and should be properly captured by the session replay system without truncation or modification.";
//        textView.setText(longText);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals(longText, thingy.getLabelText());
//    }
//
//    @Test
//    public void testTextExtractionWithSpecialCharacters() {
//        TextView textView = new TextView(context);
//        String specialText = "Special: !@#$%^&*()_+-={}[]|:;<>?,./~`";
//        textView.setText(specialText);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals(specialText, thingy.getLabelText());
//    }
//
//    @Test
//    public void testTextExtractionWithUnicodeCharacters() {
//        TextView textView = new TextView(context);
//        String unicodeText = "日本語 😀🎉 Émojis";
//        textView.setText(unicodeText);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals(unicodeText, thingy.getLabelText());
//    }
//
//    // ==================== PASSWORD DETECTION TESTS ====================
//
//    @Test
//    public void testPasswordFieldAlwaysMasked_TextPassword() {
//        TextView textView = new TextView(context);
//        textView.setText("secret123");
//        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//
//        // Even with masking disabled, password should be masked
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Password should be masked (9 asterisks for 9 characters)
//        Assert.assertEquals("*********", thingy.getLabelText());
//    }
//
//    @Test
//    public void testPasswordFieldAlwaysMasked_VisiblePassword() {
//        TextView textView = new TextView(context);
//        textView.setText("secret123");
//        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("*********", thingy.getLabelText());
//    }
//
//    @Test
//    public void testPasswordFieldAlwaysMasked_WebPassword() {
//        TextView textView = new TextView(context);
//        textView.setText("secret123");
//        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("*********", thingy.getLabelText());
//    }
//
//    @Test
//    public void testPasswordFieldAlwaysMasked_NumberPassword() {
//        TextView textView = new TextView(context);
//        textView.setText("1234");
//        textView.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("****", thingy.getLabelText());
//    }
//
//    // ==================== CONFIGURATION-BASED MASKING TESTS ====================
//
//    @Test
//    public void testMaskUserInputText_EditText() {
//        EditText editText = new EditText(context);
//        editText.setText("user input");
//
//        sessionReplayConfiguration.setMaskUserInputText(true);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, editText, agentConfiguration);
//
//        // EditText content should be masked
//        Assert.assertEquals("**********", thingy.getLabelText());
//    }
//
//    @Test
//    public void testMaskUserInputText_EditTextNotMasked() {
//        EditText editText = new EditText(context);
//        editText.setText("user input");
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, editText, agentConfiguration);
//
//        // EditText content should NOT be masked
//        Assert.assertEquals("user input", thingy.getLabelText());
//    }
//
//    @Test
//    public void testMaskApplicationText_TextView() {
//        TextView textView = new TextView(context);
//        textView.setText("app text");
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(true);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // TextView (application text) should be masked
//        Assert.assertEquals("********", thingy.getLabelText());
//    }
//
//    @Test
//    public void testMaskApplicationText_TextViewNotMasked() {
//        TextView textView = new TextView(context);
//        textView.setText("app text");
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // TextView (application text) should NOT be masked
//        Assert.assertEquals("app text", thingy.getLabelText());
//    }
//
//    @Test
//    public void testBothMaskingOptions_EditText() {
//        EditText editText = new EditText(context);
//        editText.setText("user input");
//
//        sessionReplayConfiguration.setMaskUserInputText(true);
//        sessionReplayConfiguration.setMaskApplicationText(true);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, editText, agentConfiguration);
//
//        // EditText should use maskUserInputText setting (both are true here)
//        Assert.assertEquals("**********", thingy.getLabelText());
//    }
//
//    @Test
//    public void testBothMaskingOptions_TextView() {
//        TextView textView = new TextView(context);
//        textView.setText("app text");
//
//        sessionReplayConfiguration.setMaskUserInputText(true);
//        sessionReplayConfiguration.setMaskApplicationText(true);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // TextView should use maskApplicationText setting (both are true here)
//        Assert.assertEquals("********", thingy.getLabelText());
//    }
//
//    // ==================== PRIVACY TAG TESTS ====================
//
//    @Test
//    public void testPrivacyTag_Mask() {
//        TextView textView = new TextView(context);
//        textView.setText("sensitive data");
//        textView.setTag(R.id.newrelic_privacy, "nr-mask");
//
//        sessionReplayConfiguration.setMaskApplicationText(false);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Privacy tag should force masking
//        Assert.assertEquals("**************", thingy.getLabelText());
//    }
//
//    @Test
//    public void testPrivacyTag_Unmask() {
//        TextView textView = new TextView(context);
//        textView.setText("public data");
//        textView.setTag(R.id.newrelic_privacy, "nr-unmask");
//
//        sessionReplayConfiguration.setMaskApplicationText(true);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Privacy tag should prevent masking
//        Assert.assertEquals("public data", thingy.getLabelText());
//    }
//
//    @Test
//    public void testPrivacyTag_MaskOverridesConfiguration() {
//        EditText editText = new EditText(context);
//        editText.setText("user data");
//        editText.setTag(R.id.newrelic_privacy, "nr-mask");
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, editText, agentConfiguration);
//
//        // Privacy tag should override configuration
//        Assert.assertEquals("*********", thingy.getLabelText());
//    }
//
//    @Test
//    public void testPrivacyTag_PasswordAlwaysMaskedEvenWithUnmask() {
//        TextView textView = new TextView(context);
//        textView.setText("password123");
//        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//        textView.setTag(R.id.newrelic_privacy, "nr-unmask");
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Password fields should ALWAYS be masked, even with nr-unmask tag
//        Assert.assertEquals("***********", thingy.getLabelText());
//    }
//
//    // ==================== FONT EXTRACTION TESTS ====================
//
//    @Test
//    public void testFontSizeExtraction() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample text");
//        textView.setTextSize(20f); // 20 sp
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Font size should be extracted (converted to dp)
//        Assert.assertTrue(thingy.getFontSize() > 0);
//    }
//
//    @Test
//    public void testFontFamilyExtraction_Default() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample text");
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertNotNull(thingy.getFontFamily());
//        Assert.assertEquals("sans-serif", thingy.getFontFamily());
//    }
//
//    @Test
//    public void testFontFamilyExtraction_Monospace() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample text");
//        textView.setTypeface(Typeface.MONOSPACE);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("monospace", thingy.getFontFamily());
//    }
//
//    @Test
//    public void testFontFamilyExtraction_Serif() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample text");
//        textView.setTypeface(Typeface.SERIF);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("serif", thingy.getFontFamily());
//    }
//
//    // ==================== COLOR EXTRACTION TESTS ====================
//
//    @Test
//    public void testTextColorExtraction_Black() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample text");
//        textView.setTextColor(Color.BLACK);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Black color should be "000000" (RGB, alpha masked off)
//        Assert.assertEquals("000000", thingy.getTextColor());
//    }
//
//    @Test
//    public void testTextColorExtraction_White() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample text");
//        textView.setTextColor(Color.WHITE);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // White color should be "ffffff"
//        Assert.assertEquals("ffffff", thingy.getTextColor());
//    }
//
//    @Test
//    public void testTextColorExtraction_CustomColor() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample text");
//        textView.setTextColor(Color.rgb(255, 0, 0)); // Red
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Red color should be "ff0000"
//        Assert.assertEquals("ff0000", thingy.getTextColor());
//    }
//
//    // ==================== TEXT ALIGNMENT TESTS ====================
//
//    @Test
//    public void testTextAlignment_Left() {
//        TextView textView = new TextView(context);
//        textView.setText("Left aligned");
//        textView.setGravity(Gravity.LEFT);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("left", thingy.getTextAlign());
//    }
//
//    @Test
//    public void testTextAlignment_Center() {
//        TextView textView = new TextView(context);
//        textView.setText("Center aligned");
//        textView.setGravity(Gravity.CENTER_HORIZONTAL);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("center", thingy.getTextAlign());
//    }
//
//    @Test
//    public void testTextAlignment_Right() {
//        TextView textView = new TextView(context);
//        textView.setText("Right aligned");
//        textView.setGravity(Gravity.RIGHT);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertEquals("right", thingy.getTextAlign());
//    }
//
//    @Test
//    public void testTextAlignment_ExplicitTextAlignmentTakesPrecedence() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample text");
//        textView.setGravity(Gravity.LEFT);
//        textView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Explicit textAlignment should override gravity
//        Assert.assertEquals("center", thingy.getTextAlign());
//    }
//
//    // ==================== RRWEB NODE GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateRRWebNode() {
//        TextView textView = new TextView(context);
//        textView.setText("Test");
//        textView.setTextSize(16f);
//        textView.setTextColor(Color.BLACK);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node);
//        Assert.assertNotNull(node.getAttributes());
//        Assert.assertNotNull(node.getChildNodes());
//    }
//
//    @Test
//    public void testGenerateRRWebNode_TextContentIncluded() {
//        TextView textView = new TextView(context);
//        textView.setText("Hello RRWeb");
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        // Text should be included in child nodes
//        Assert.assertNotNull(node.getChildNodes());
//        Assert.assertFalse(node.getChildNodes().isEmpty());
//    }
//
//    @Test
//    public void testGenerateRRWebNode_MaskedText() {
//        TextView textView = new TextView(context);
//        textView.setText("secret");
//        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node);
//        // The text in child nodes should be masked
//        Assert.assertNotNull(node.getChildNodes());
//    }
//
//    // ==================== VIEW DETAILS TESTS ====================
//
//    @Test
//    public void testGetViewDetails() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample");
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertNotNull(thingy.getViewDetails());
//        Assert.assertEquals(viewDetails, thingy.getViewDetails());
//    }
//
//    @Test
//    public void testShouldRecordSubviews() {
//        TextView textView = new TextView(context);
//        textView.setText("Sample");
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // TextViews should not record subviews
//        Assert.assertFalse(thingy.shouldRecordSubviews());
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testMaskingWithEmptyPassword() {
//        TextView textView = new TextView(context);
//        textView.setText("");
//        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Empty password should result in empty masked string
//        Assert.assertEquals("", thingy.getLabelText());
//    }
//
//    @Test
//    public void testMaskingWithSingleCharacter() {
//        TextView textView = new TextView(context);
//        textView.setText("x");
//        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        // Single character should be masked with single asterisk
//        Assert.assertEquals("*", thingy.getLabelText());
//    }
//
//    @Test
//    public void testTextViewInViewGroup() {
//        LinearLayout parent = new LinearLayout(context);
//        TextView textView = new TextView(context);
//        textView.setText("Child text");
//        parent.addView(textView);
//
//        ViewDetails viewDetails = new ViewDetails(textView);
//        SessionReplayTextViewThingy thingy = new SessionReplayTextViewThingy(viewDetails, textView, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals("Child text", thingy.getLabelText());
//    }
//}