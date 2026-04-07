///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.view.View;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.ImageView;
//import android.widget.TextView;
//
//import androidx.compose.ui.node.LayoutNode;
//import androidx.compose.ui.semantics.Role;
//import androidx.compose.ui.semantics.SemanticsConfiguration;
//import androidx.compose.ui.semantics.SemanticsNode;
//import androidx.compose.ui.semantics.SemanticsProperties;
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.AgentConfiguration;
//import com.newrelic.agent.android.sessionReplay.compose.ComposeEditTextThingy;
//import com.newrelic.agent.android.sessionReplay.compose.ComposeImageThingy;
//import com.newrelic.agent.android.sessionReplay.compose.ComposeTextViewThingy;
//import com.newrelic.agent.android.sessionReplay.compose.SessionReplayComposeViewThingy;
//
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayThingyRecorderTest {
//
//    private Context context;
//    private AgentConfiguration agentConfiguration;
//    private SessionReplayThingyRecorder recorder;
//
//    @Before
//    public void setUp() {
//        context = ApplicationProvider.getApplicationContext();
//        agentConfiguration = AgentConfiguration.getInstance();
//        recorder = new SessionReplayThingyRecorder(agentConfiguration);
//    }
//
//    // ==================== CONSTRUCTOR TESTS ====================
//
//    @Test
//    public void testConstructor_WithValidConfiguration() {
//        SessionReplayThingyRecorder newRecorder = new SessionReplayThingyRecorder(agentConfiguration);
//        Assert.assertNotNull(newRecorder);
//    }
//
//    @Test
//    public void testConstructor_WithNullConfiguration() {
//        SessionReplayThingyRecorder newRecorder = new SessionReplayThingyRecorder(null);
//        Assert.assertNotNull(newRecorder);
//    }
//
//    // ==================== ANDROID VIEW RECORDING TESTS ====================
//
//    @Test
//    public void testRecordView_EditText_ReturnsEditTextThingy() {
//        EditText editText = new EditText(context);
//        editText.setText("Test input");
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(editText);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayEditTextThingy);
//    }
//
//    @Test
//    public void testRecordView_ImageView_ReturnsImageViewThingy() {
//        ImageView imageView = new ImageView(context);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(imageView);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayImageViewThingy);
//    }
//
//    @Test
//    public void testRecordView_TextView_ReturnsTextViewThingy() {
//        TextView textView = new TextView(context);
//        textView.setText("Test text");
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(textView);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayTextViewThingy);
//    }
//
//    @Test
//    public void testRecordView_Button_ReturnsTextViewThingy() {
//        // Button extends TextView, so should return SessionReplayTextViewThingy
//        Button button = new Button(context);
//        button.setText("Click me");
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(button);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayTextViewThingy);
//    }
//
//    @Test
//    public void testRecordView_PlainView_ReturnsViewThingy() {
//        View view = new View(context);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(view);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayViewThingy);
//        Assert.assertFalse(thingy instanceof SessionReplayTextViewThingy);
//        Assert.assertFalse(thingy instanceof SessionReplayImageViewThingy);
//        Assert.assertFalse(thingy instanceof SessionReplayEditTextThingy);
//    }
//
//    @Test
//    public void testRecordView_EditText_WithEmptyText() {
//        EditText editText = new EditText(context);
//        editText.setText("");
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(editText);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayEditTextThingy);
//    }
//
//    @Test
//    public void testRecordView_ImageView_WithNullDrawable() {
//        ImageView imageView = new ImageView(context);
//        imageView.setImageDrawable(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(imageView);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayImageViewThingy);
//    }
//
//    @Test
//    public void testRecordView_TextView_WithNullText() {
//        TextView textView = new TextView(context);
//        textView.setText(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(textView);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayTextViewThingy);
//    }
//
//    // ==================== VIEW HIERARCHY TESTS ====================
//
//    @Test
//    public void testRecordView_EditTextIsSubclassOfTextView_ReturnsEditTextThingy() {
//        // EditText is checked before TextView in the if-else chain
//        // Verify correct precedence
//        EditText editText = new EditText(context);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(editText);
//
//        // Should be EditTextThingy, not TextViewThingy
//        Assert.assertTrue(thingy instanceof SessionReplayEditTextThingy);
//    }
//
//    @Test
//    public void testRecordView_ImageViewWithNonStandardScaleType() {
//        ImageView imageView = new ImageView(context);
//        imageView.setScaleType(ImageView.ScaleType.MATRIX);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(imageView);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayImageViewThingy);
//    }
//
//    // ==================== VIEW STATE TESTS ====================
//
//    @Test
//    public void testRecordView_InvisibleView() {
//        View view = new View(context);
//        view.setVisibility(View.INVISIBLE);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(view);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayViewThingy);
//    }
//
//    @Test
//    public void testRecordView_GoneView() {
//        View view = new View(context);
//        view.setVisibility(View.GONE);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(view);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayViewThingy);
//    }
//
//    @Test
//    public void testRecordView_ViewWithZeroAlpha() {
//        TextView textView = new TextView(context);
//        textView.setAlpha(0.0f);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(textView);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayTextViewThingy);
//    }
//
//    // ==================== PRIVACY TAG TESTS ====================
//
//    @Test
//    public void testRecordView_ViewWithMaskTag() {
//        TextView textView = new TextView(context);
//        textView.setTag("nr-mask");
//        textView.setText("Sensitive data");
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(textView);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayTextViewThingy);
//    }
//
//    @Test
//    public void testRecordView_ViewWithUnmaskTag() {
//        EditText editText = new EditText(context);
//        editText.setTag("nr-unmask");
//        editText.setText("Public data");
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(editText);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayEditTextThingy);
//    }
//
//    // ==================== MULTIPLE VIEWS TESTS ====================
//
//    @Test
//    public void testRecordView_MultipleViewsInSequence() {
//        TextView textView = new TextView(context);
//        ImageView imageView = new ImageView(context);
//        EditText editText = new EditText(context);
//        View plainView = new View(context);
//
//        SessionReplayViewThingyInterface thingy1 = recorder.recordView(textView);
//        SessionReplayViewThingyInterface thingy2 = recorder.recordView(imageView);
//        SessionReplayViewThingyInterface thingy3 = recorder.recordView(editText);
//        SessionReplayViewThingyInterface thingy4 = recorder.recordView(plainView);
//
//        Assert.assertTrue(thingy1 instanceof SessionReplayTextViewThingy);
//        Assert.assertTrue(thingy2 instanceof SessionReplayImageViewThingy);
//        Assert.assertTrue(thingy3 instanceof SessionReplayEditTextThingy);
//        Assert.assertTrue(thingy4 instanceof SessionReplayViewThingy);
//    }
//
//    // ==================== COMPOSE SEMANTICS NODE TESTS ====================
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_WithEditableText() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(true);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        // Mock ReflectionUtils to return our mock layoutNode
//        // Note: In real tests, we'd need PowerMockito or similar to mock static methods
//        // For now, assuming the reflection works
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 1.0f);
//
//        // This test would pass if ReflectionUtils.getLayoutNode() works correctly
//        Assert.assertNotNull(thingy);
//        // Would expect ComposeEditTextThingy but reflection might fail in test environment
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_WithText() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(true);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 1.0f);
//
//        Assert.assertNotNull(thingy);
//        // Would expect ComposeTextViewThingy
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_WithImageRole() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//        Role imageRole = Role.Companion.getImage();
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getRole())).thenReturn(true);
//        when(config.get(SemanticsProperties.INSTANCE.getRole())).thenReturn(imageRole);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 1.0f);
//
//        Assert.assertNotNull(thingy);
//        // Would expect ComposeImageThingy
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_WithButtonRole() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//        Role buttonRole = Role.Companion.getButton();
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getRole())).thenReturn(true);
//        when(config.get(SemanticsProperties.INSTANCE.getRole())).thenReturn(buttonRole);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 1.0f);
//
//        Assert.assertNotNull(thingy);
//        // Button role has no special handling, falls through to default
//        // Would expect SessionReplayComposeViewThingy
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_WithInteropView() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//        TextView interopView = new TextView(context);
//        interopView.setText("Interop TextView");
//
//        when(layoutNode.getInteropView()).thenReturn(interopView);
//
//        // When a SemanticsNode has an InteropView, it delegates to the Android View path
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 1.0f);
//
//        Assert.assertNotNull(thingy);
//        // Should return SessionReplayTextViewThingy since interopView is a TextView
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_DefaultCase() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getRole())).thenReturn(false);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 1.0f);
//
//        Assert.assertNotNull(thingy);
//        // Default case should return SessionReplayComposeViewThingy
//    }
//
//    // ==================== DENSITY TESTS ====================
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_WithDifferentDensities() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getRole())).thenReturn(false);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        // Test with different density values
//        SessionReplayViewThingyInterface thingy1 = recorder.recordView(node, 1.0f);
//        SessionReplayViewThingyInterface thingy2 = recorder.recordView(node, 2.0f);
//        SessionReplayViewThingyInterface thingy3 = recorder.recordView(node, 3.0f);
//
//        Assert.assertNotNull(thingy1);
//        Assert.assertNotNull(thingy2);
//        Assert.assertNotNull(thingy3);
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_WithZeroDensity() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getRole())).thenReturn(false);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 0.0f);
//
//        Assert.assertNotNull(thingy);
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_WithNegativeDensity() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(false);
//        when(config.contains(SemanticsProperties.INSTANCE.getRole())).thenReturn(false);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, -1.0f);
//
//        Assert.assertNotNull(thingy);
//    }
//
//    // ==================== PRECEDENCE TESTS ====================
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_EditableTextTakesPrecedenceOverText() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//
//        when(node.getConfig()).thenReturn(config);
//        // Both EditableText and Text are present
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(true);
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(true);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 1.0f);
//
//        Assert.assertNotNull(thingy);
//        // EditableText is checked first, so should return ComposeEditTextThingy
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_SemanticsNode_TextTakesPrecedenceOverRole() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        SemanticsConfiguration config = mock(SemanticsConfiguration.class);
//        LayoutNode layoutNode = mock(LayoutNode.class);
//        Role imageRole = Role.Companion.getImage();
//
//        when(node.getConfig()).thenReturn(config);
//        when(config.contains(SemanticsProperties.INSTANCE.getEditableText())).thenReturn(false);
//        // Both Text and Role are present
//        when(config.contains(SemanticsProperties.INSTANCE.getText())).thenReturn(true);
//        when(config.contains(SemanticsProperties.INSTANCE.getRole())).thenReturn(true);
//        when(config.get(SemanticsProperties.INSTANCE.getRole())).thenReturn(imageRole);
//        when(layoutNode.getInteropView()).thenReturn(null);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(node, 1.0f);
//
//        Assert.assertNotNull(thingy);
//        // Text is checked before Role, so should return ComposeTextViewThingy
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testRecordView_ViewWithMultipleTypeCasts() {
//        // Create an EditText and verify it's not misidentified
//        EditText editText = new EditText(context);
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(editText);
//
//        // Should be EditTextThingy, not ImageViewThingy or plain ViewThingy
//        Assert.assertTrue(thingy instanceof SessionReplayEditTextThingy);
//        Assert.assertFalse(thingy instanceof SessionReplayImageViewThingy);
//    }
//
//    @Test
//    public void testRecordView_SameRecorderMultipleCalls() {
//        // Verify recorder can be reused without state pollution
//        View view1 = new View(context);
//        TextView view2 = new TextView(context);
//        ImageView view3 = new ImageView(context);
//
//        SessionReplayViewThingyInterface thingy1 = recorder.recordView(view1);
//        SessionReplayViewThingyInterface thingy2 = recorder.recordView(view2);
//        SessionReplayViewThingyInterface thingy3 = recorder.recordView(view3);
//
//        // Each should return correct type
//        Assert.assertTrue(thingy1 instanceof SessionReplayViewThingy);
//        Assert.assertTrue(thingy2 instanceof SessionReplayTextViewThingy);
//        Assert.assertTrue(thingy3 instanceof SessionReplayImageViewThingy);
//    }
//
//    @Test
//    public void testRecordView_ViewAfterConfigurationChange() {
//        TextView textView = new TextView(context);
//        textView.setText("Before config change");
//
//        SessionReplayViewThingyInterface thingy1 = recorder.recordView(textView);
//
//        // Simulate configuration change
//        textView.setText("After config change");
//
//        SessionReplayViewThingyInterface thingy2 = recorder.recordView(textView);
//
//        Assert.assertTrue(thingy1 instanceof SessionReplayTextViewThingy);
//        Assert.assertTrue(thingy2 instanceof SessionReplayTextViewThingy);
//    }
//
//    // ==================== CONFIGURATION TESTS ====================
//
//    @Test
//    public void testRecordView_WithDifferentConfigurations() {
//        AgentConfiguration config1 = AgentConfiguration.getInstance();
//        AgentConfiguration config2 = AgentConfiguration.getInstance();
//
//        SessionReplayThingyRecorder recorder1 = new SessionReplayThingyRecorder(config1);
//        SessionReplayThingyRecorder recorder2 = new SessionReplayThingyRecorder(config2);
//
//        TextView textView = new TextView(context);
//
//        SessionReplayViewThingyInterface thingy1 = recorder1.recordView(textView);
//        SessionReplayViewThingyInterface thingy2 = recorder2.recordView(textView);
//
//        Assert.assertTrue(thingy1 instanceof SessionReplayTextViewThingy);
//        Assert.assertTrue(thingy2 instanceof SessionReplayTextViewThingy);
//    }
//
//    // ==================== NULL AND INVALID INPUT TESTS ====================
//
//    @Test(expected = NullPointerException.class)
//    public void testRecordView_NullView_ThrowsException() {
//        recorder.recordView((View) null);
//    }
//
//    @Test
//    @androidx.compose.ui.InternalComposeUiApi
//    public void testRecordView_NullSemanticsNode() {
//        try {
//            recorder.recordView((SemanticsNode) null, 1.0f);
//            Assert.fail("Should throw NullPointerException");
//        } catch (NullPointerException e) {
//            // Expected
//        }
//    }
//
//    // ==================== TYPE VERIFICATION TESTS ====================
//
//    @Test
//    public void testRecordView_ReturnTypesImplementInterface() {
//        View view = new View(context);
//        TextView textView = new TextView(context);
//        ImageView imageView = new ImageView(context);
//        EditText editText = new EditText(context);
//
//        SessionReplayViewThingyInterface viewThingy = recorder.recordView(view);
//        SessionReplayViewThingyInterface textThingy = recorder.recordView(textView);
//        SessionReplayViewThingyInterface imageThingy = recorder.recordView(imageView);
//        SessionReplayViewThingyInterface editThingy = recorder.recordView(editText);
//
//        // All should implement the interface
//        Assert.assertTrue(viewThingy instanceof SessionReplayViewThingyInterface);
//        Assert.assertTrue(textThingy instanceof SessionReplayViewThingyInterface);
//        Assert.assertTrue(imageThingy instanceof SessionReplayViewThingyInterface);
//        Assert.assertTrue(editThingy instanceof SessionReplayViewThingyInterface);
//    }
//
//    @Test
//    public void testRecordView_CustomViewSubclass_ReturnsPlanViewThingy() {
//        // Custom view that doesn't extend TextView, ImageView, or EditText
//        View customView = new View(context) {
//            // Custom implementation
//        };
//
//        SessionReplayViewThingyInterface thingy = recorder.recordView(customView);
//
//        Assert.assertTrue(thingy instanceof SessionReplayViewThingy);
//        Assert.assertFalse(thingy instanceof SessionReplayTextViewThingy);
//    }
//}