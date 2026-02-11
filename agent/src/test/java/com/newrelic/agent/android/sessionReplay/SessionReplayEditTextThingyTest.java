///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.graphics.Color;
//import android.text.InputType;
//import android.widget.EditText;
//import android.widget.LinearLayout;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.AgentConfiguration;
//import com.newrelic.agent.android.R;
//import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
//import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
//
//import org.junit.After;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//import java.util.List;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayEditTextThingyTest {
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
//    // ==================== CONSTRUCTOR AND INHERITANCE TESTS ====================
//
//    @Test
//    public void testConstructorWithBasicEditText() {
//        EditText editText = new EditText(context);
//        editText.setText("user input");
//        editText.setHint("Enter text here");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        // EditTextThingy extends TextViewThingy, so it should have labelText
//        Assert.assertEquals("user input", thingy.getLabelText());
//    }
//
//    @Test
//    public void testInheritsFromTextViewThingy() {
//        EditText editText = new EditText(context);
//        editText.setText("test");
//        editText.setTextSize(16f);
//        editText.setTextColor(Color.BLACK);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Should inherit all TextView properties
//        Assert.assertNotNull(thingy.getLabelText());
//        Assert.assertTrue(thingy.getFontSize() > 0);
//        Assert.assertNotNull(thingy.getFontFamily());
//        Assert.assertNotNull(thingy.getTextColor());
//        Assert.assertNotNull(thingy.getTextAlign());
//    }
//
//    // ==================== HINT TEXT EXTRACTION TESTS ====================
//
//    @Test
//    public void testHintTextExtraction() {
//        EditText editText = new EditText(context);
//        editText.setHint("Enter your name");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Hint should be extracted (accessible via generateRRWebNode())
//        Assert.assertNotNull(thingy);
//    }
//
//    @Test
//    public void testHintTextWithEmptyText() {
//        EditText editText = new EditText(context);
//        editText.setText("");
//        editText.setHint("Enter your name");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // When text is empty, hint should be shown in RRWeb node
//        RRWebElementNode node = thingy.generateRRWebNode();
//        Assert.assertNotNull(node);
//        Assert.assertNotNull(node.getChildNodes());
//        Assert.assertFalse(node.getChildNodes().isEmpty());
//    }
//
//    @Test
//    public void testHintTextWithNullHint() {
//        EditText editText = new EditText(context);
//        editText.setText("user text");
//        editText.setHint(null);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals("user text", thingy.getLabelText());
//    }
//
//    @Test
//    public void testHintTextWithEmptyHint() {
//        EditText editText = new EditText(context);
//        editText.setText("user text");
//        editText.setHint("");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals("user text", thingy.getLabelText());
//    }
//
//    @Test
//    public void testHintTextWithLongHint() {
//        EditText editText = new EditText(context);
//        String longHint = "This is a very long hint text that provides detailed instructions to the user about what they should enter in this field.";
//        editText.setHint(longHint);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//    }
//
//    @Test
//    public void testHintTextWithSpecialCharacters() {
//        EditText editText = new EditText(context);
//        editText.setHint("Enter: !@#$%^&*()");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//    }
//
//    @Test
//    public void testHintTextWithUnicodeCharacters() {
//        EditText editText = new EditText(context);
//        editText.setHint("入力してください 😀");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//    }
//
//    // ==================== HINT MASKING TESTS ====================
//
//    @Test
//    public void testHintMasking_InputTypeSet() {
//        EditText editText = new EditText(context);
//        editText.setInputType(InputType.TYPE_CLASS_TEXT);
//        editText.setHint("Enter password");
//
//        sessionReplayConfiguration.setMaskUserInputText(true);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Hint should be masked when inputType is set and maskUserInputText is true
//        Assert.assertNotNull(thingy);
//    }
//
//    @Test
//    public void testHintMasking_NoInputType() {
//        EditText editText = new EditText(context);
//        editText.setInputType(0); // No input type
//        editText.setHint("Display hint");
//
//        sessionReplayConfiguration.setMaskApplicationText(true);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Hint should be masked based on maskApplicationText when inputType is 0
//        Assert.assertNotNull(thingy);
//    }
//
//    @Test
//    public void testHintNotMasked_WhenConfigurationDisabled() {
//        EditText editText = new EditText(context);
//        editText.setInputType(InputType.TYPE_CLASS_TEXT);
//        editText.setHint("visible hint");
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//    }
//
//    // ==================== PASSWORD FIELD TESTS (INHERITED) ====================
//
//    @Test
//    public void testPasswordFieldWithHint() {
//        EditText editText = new EditText(context);
//        editText.setText("password123");
//        editText.setHint("Enter password");
//        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Password text should always be masked (inherited behavior)
//        Assert.assertEquals("***********", thingy.getLabelText());
//    }
//
//    @Test
//    public void testEmptyPasswordFieldShowsHint() {
//        EditText editText = new EditText(context);
//        editText.setText("");
//        editText.setHint("Password");
//        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // When password is empty, hint should be shown
//        RRWebElementNode node = thingy.generateRRWebNode();
//        Assert.assertNotNull(node);
//        Assert.assertNotNull(node.getChildNodes());
//    }
//
//    // ==================== RRWEB NODE GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateRRWebNode_WithText() {
//        EditText editText = new EditText(context);
//        editText.setText("user input");
//        editText.setHint("hint text");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node);
//        Assert.assertNotNull(node.getAttributes());
//        Assert.assertEquals("text", node.getAttributes().getType()); // EditText sets type="text"
//        Assert.assertNotNull(node.getChildNodes());
//        // Should show actual text, not hint
//    }
//
//    @Test
//    public void testGenerateRRWebNode_EmptyTextShowsHint() {
//        EditText editText = new EditText(context);
//        editText.setText("");
//        editText.setHint("hint text");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node);
//        Assert.assertNotNull(node.getChildNodes());
//        // When text is empty, should show hint
//    }
//
//    @Test
//    public void testGenerateRRWebNode_BothEmpty() {
//        EditText editText = new EditText(context);
//        editText.setText("");
//        editText.setHint("");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node);
//        Assert.assertNotNull(node.getChildNodes());
//    }
//
//    @Test
//    public void testGenerateRRWebNode_AttributesContainType() {
//        EditText editText = new EditText(context);
//        editText.setText("test");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node.getAttributes());
//        Assert.assertEquals("text", node.getAttributes().getType());
//    }
//
//    // ==================== CSS GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateCssDescription() {
//        EditText editText = new EditText(context);
//        editText.setText("test");
//        editText.setTextSize(16f);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        String css = thingy.generateCssDescription();
//
//        Assert.assertNotNull(css);
//        Assert.assertFalse(css.isEmpty());
//    }
//
//    @Test
//    public void testGenerateInlineCss() {
//        EditText editText = new EditText(context);
//        editText.setText("test");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        String inlineCss = thingy.generateInlineCss();
//
//        Assert.assertNotNull(inlineCss);
//    }
//
//    // ==================== INCREMENTAL DIFF TESTS ====================
//
//    @Test
//    public void testGenerateDifferences_SameContent() {
//        EditText editText1 = new EditText(context);
//        editText1.setText("same text");
//
//        EditText editText2 = new EditText(context);
//        editText2.setText("same text");
//
//        ViewDetails viewDetails1 = new ViewDetails(editText1);
//        ViewDetails viewDetails2 = new ViewDetails(editText2);
//
//        SessionReplayEditTextThingy thingy1 = new SessionReplayEditTextThingy(viewDetails1, editText1, agentConfiguration);
//        SessionReplayEditTextThingy thingy2 = new SessionReplayEditTextThingy(viewDetails2, editText2, agentConfiguration);
//
//        List<MutationRecord> diffs = thingy1.generateDifferences(thingy2);
//
//        // May have some differences due to frame/position, but should not crash
//        Assert.assertNotNull(diffs);
//    }
//
//    @Test
//    public void testGenerateDifferences_DifferentText() {
//        EditText editText1 = new EditText(context);
//        editText1.setText("old text");
//
//        EditText editText2 = new EditText(context);
//        editText2.setText("new text");
//
//        ViewDetails viewDetails1 = new ViewDetails(editText1);
//        ViewDetails viewDetails2 = new ViewDetails(editText2);
//
//        SessionReplayEditTextThingy thingy1 = new SessionReplayEditTextThingy(viewDetails1, editText1, agentConfiguration);
//        SessionReplayEditTextThingy thingy2 = new SessionReplayEditTextThingy(viewDetails2, editText2, agentConfiguration);
//
//        List<MutationRecord> diffs = thingy1.generateDifferences(thingy2);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertFalse(diffs.isEmpty()); // Should detect text change
//    }
//
//    @Test
//    public void testGenerateDifferences_DifferentColor() {
//        EditText editText1 = new EditText(context);
//        editText1.setText("text");
//        editText1.setTextColor(Color.BLACK);
//
//        EditText editText2 = new EditText(context);
//        editText2.setText("text");
//        editText2.setTextColor(Color.RED);
//
//        ViewDetails viewDetails1 = new ViewDetails(editText1);
//        ViewDetails viewDetails2 = new ViewDetails(editText2);
//
//        SessionReplayEditTextThingy thingy1 = new SessionReplayEditTextThingy(viewDetails1, editText1, agentConfiguration);
//        SessionReplayEditTextThingy thingy2 = new SessionReplayEditTextThingy(viewDetails2, editText2, agentConfiguration);
//
//        List<MutationRecord> diffs = thingy1.generateDifferences(thingy2);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertFalse(diffs.isEmpty()); // Should detect color change
//    }
//
//    @Test
//    public void testGenerateDifferences_WithNonEditTextThingy() {
//        EditText editText = new EditText(context);
//        editText.setText("text");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy editTextThingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Create a generic view thingy (different type)
//        LinearLayout layout = new LinearLayout(context);
//        ViewDetails layoutDetails = new ViewDetails(layout);
//        SessionReplayViewThingy layoutThingy = new SessionReplayViewThingy(layoutDetails, agentConfiguration);
//
//        List<MutationRecord> diffs = editTextThingy.generateDifferences(layoutThingy);
//
//        // Should return empty list when comparing different types
//        Assert.assertNotNull(diffs);
//        Assert.assertTrue(diffs.isEmpty());
//    }
//
//    @Test
//    public void testGenerateDifferences_WithNull() {
//        EditText editText = new EditText(context);
//        editText.setText("text");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        List<MutationRecord> diffs = thingy.generateDifferences(null);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertTrue(diffs.isEmpty());
//    }
//
//    // ==================== CHANGE DETECTION TESTS ====================
//
//    @Test
//    public void testHasChanged_SameContent() {
//        EditText editText1 = new EditText(context);
//        editText1.setText("same");
//
//        EditText editText2 = new EditText(context);
//        editText2.setText("same");
//
//        ViewDetails viewDetails1 = new ViewDetails(editText1);
//        ViewDetails viewDetails2 = new ViewDetails(editText2);
//
//        SessionReplayEditTextThingy thingy1 = new SessionReplayEditTextThingy(viewDetails1, editText1, agentConfiguration);
//        SessionReplayEditTextThingy thingy2 = new SessionReplayEditTextThingy(viewDetails2, editText2, agentConfiguration);
//
//        // hasChanged uses hashCode comparison
//        boolean changed = thingy1.hasChanged(thingy2);
//        Assert.assertNotNull(changed); // Result depends on hashCode implementation
//    }
//
//    @Test
//    public void testHasChanged_DifferentContent() {
//        EditText editText1 = new EditText(context);
//        editText1.setText("old");
//
//        EditText editText2 = new EditText(context);
//        editText2.setText("new");
//
//        ViewDetails viewDetails1 = new ViewDetails(editText1);
//        ViewDetails viewDetails2 = new ViewDetails(editText2);
//
//        SessionReplayEditTextThingy thingy1 = new SessionReplayEditTextThingy(viewDetails1, editText1, agentConfiguration);
//        SessionReplayEditTextThingy thingy2 = new SessionReplayEditTextThingy(viewDetails2, editText2, agentConfiguration);
//
//        boolean changed = thingy1.hasChanged(thingy2);
//        Assert.assertTrue(changed); // Different content should trigger change
//    }
//
//    @Test
//    public void testHasChanged_WithNull() {
//        EditText editText = new EditText(context);
//        editText.setText("text");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        boolean changed = thingy.hasChanged(null);
//        Assert.assertTrue(changed); // null should always indicate change
//    }
//
//    @Test
//    public void testHasChanged_WithDifferentType() {
//        EditText editText = new EditText(context);
//        editText.setText("text");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy editTextThingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        LinearLayout layout = new LinearLayout(context);
//        ViewDetails layoutDetails = new ViewDetails(layout);
//        SessionReplayViewThingy layoutThingy = new SessionReplayViewThingy(layoutDetails, agentConfiguration);
//
//        boolean changed = editTextThingy.hasChanged(layoutThingy);
//        Assert.assertTrue(changed); // Different types should indicate change
//    }
//
//    // ==================== PRIVACY TAG TESTS (INHERITED) ====================
//
//    @Test
//    public void testPrivacyTag_Mask_OnEditText() {
//        EditText editText = new EditText(context);
//        editText.setText("sensitive input");
//        editText.setTag(R.id.newrelic_privacy, "nr-mask");
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Privacy tag should force masking (inherited behavior)
//        Assert.assertEquals("****************", thingy.getLabelText());
//    }
//
//    @Test
//    public void testPrivacyTag_Unmask_OnEditText() {
//        EditText editText = new EditText(context);
//        editText.setText("public input");
//        editText.setTag(R.id.newrelic_privacy, "nr-unmask");
//
//        sessionReplayConfiguration.setMaskUserInputText(true);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Privacy tag should prevent masking (inherited behavior)
//        Assert.assertEquals("public input", thingy.getLabelText());
//    }
//
//    // ==================== VIEW DETAILS TESTS ====================
//
//    @Test
//    public void testGetViewDetails() {
//        EditText editText = new EditText(context);
//        editText.setText("test");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy.getViewDetails());
//        Assert.assertEquals(viewDetails, thingy.getViewDetails());
//    }
//
//    @Test
//    public void testGetViewId() {
//        EditText editText = new EditText(context);
//        editText.setText("test");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        int viewId = thingy.getViewId();
//        Assert.assertEquals(viewDetails.viewId, viewId);
//    }
//
//    @Test
//    public void testGetParentViewId() {
//        LinearLayout parent = new LinearLayout(context);
//        EditText editText = new EditText(context);
//        editText.setText("child");
//        parent.addView(editText);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        int parentId = thingy.getParentViewId();
//        Assert.assertEquals(viewDetails.parentId, parentId);
//    }
//
//    @Test
//    public void testShouldRecordSubviews() {
//        EditText editText = new EditText(context);
//        editText.setText("test");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // EditText should not record subviews
//        Assert.assertFalse(thingy.shouldRecordSubviews());
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testMultilineEditText() {
//        EditText editText = new EditText(context);
//        editText.setText("Line 1\nLine 2\nLine 3");
//        editText.setHint("Enter multiple lines");
//        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals("Line 1\nLine 2\nLine 3", thingy.getLabelText());
//    }
//
//    @Test
//    public void testEmailInputType() {
//        EditText editText = new EditText(context);
//        editText.setText("user@example.com");
//        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
//
//        sessionReplayConfiguration.setMaskUserInputText(true);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        // Email should be masked when maskUserInputText is true
//        Assert.assertEquals("*****************", thingy.getLabelText());
//    }
//
//    @Test
//    public void testNumberInputType() {
//        EditText editText = new EditText(context);
//        editText.setText("12345");
//        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
//
//        sessionReplayConfiguration.setMaskUserInputText(false);
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        Assert.assertEquals("12345", thingy.getLabelText());
//    }
//
//    @Test
//    public void testGetSubviews() {
//        EditText editText = new EditText(context);
//        editText.setText("test");
//
//        ViewDetails viewDetails = new ViewDetails(editText);
//        SessionReplayEditTextThingy thingy = new SessionReplayEditTextThingy(viewDetails, editText, agentConfiguration);
//
//        List<? extends SessionReplayViewThingyInterface> subviews = thingy.getSubviews();
//        Assert.assertNotNull(subviews);
//        Assert.assertTrue(subviews.isEmpty());
//    }
//}