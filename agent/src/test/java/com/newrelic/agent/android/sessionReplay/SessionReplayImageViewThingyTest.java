///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.Color;
//import android.graphics.drawable.BitmapDrawable;
//import android.graphics.drawable.ColorDrawable;
//import android.graphics.drawable.Drawable;
//import android.graphics.drawable.InsetDrawable;
//import android.graphics.drawable.LayerDrawable;
//import android.widget.FrameLayout;
//import android.widget.ImageView;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.AgentConfiguration;
//import com.newrelic.agent.android.R;
//import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
//import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
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
//public class SessionReplayImageViewThingyTest {
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
//        sessionReplayConfiguration.setMaskAllImages(false);
//
//        sessionReplayLocalConfiguration = new SessionReplayLocalConfiguration();
//
//        agentConfiguration.setSessionReplayConfiguration(sessionReplayConfiguration);
//        agentConfiguration.setSessionReplayLocalConfiguration(sessionReplayLocalConfiguration);
//
//        // Clear cache before each test
//        SessionReplayImageViewThingy.clearImageCache();
//    }
//
//    @After
//    public void tearDown() {
//        sessionReplayLocalConfiguration.clearAllViewMasks();
//        SessionReplayImageViewThingy.clearImageCache();
//    }
//
//    // ==================== CONSTRUCTOR TESTS ====================
//
//    @Test
//    public void testConstructorWithBasicImageView() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertNotNull(thingy.getImageData());
//    }
//
//    @Test
//    public void testConstructorWithNullDrawable() {
//        ImageView imageView = new ImageView(context);
//        // No image set - drawable is null
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertNull(thingy.getImageData());
//    }
//
//    @Test
//    public void testConstructorWithMaskingEnabled() {
//        sessionReplayConfiguration.setMaskAllImages(true);
//
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertNull(thingy.getImageData()); // Image should be masked (null)
//    }
//
//    @Test
//    public void testConstructorWithColorBackground() {
//        ImageView imageView = new ImageView(context);
//        imageView.setBackgroundColor(Color.RED);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        String inlineCss = thingy.generateInlineCss();
//        Assert.assertTrue(inlineCss.contains("background-color:"));
//    }
//
//    // ==================== IMAGE EXTRACTION TESTS ====================
//
//    @Test
//    public void testGetImageDataWithBitmapDrawable() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String imageData = thingy.getImageData();
//        Assert.assertNotNull(imageData);
//        Assert.assertTrue(imageData.length() > 0);
//    }
//
//    @Test
//    public void testGetImageDataWithLayerDrawable() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap1 = createTestBitmap(10, 10);
//        Bitmap bitmap2 = createTestBitmap(10, 10);
//
//        BitmapDrawable drawable1 = new BitmapDrawable(context.getResources(), bitmap1);
//        BitmapDrawable drawable2 = new BitmapDrawable(context.getResources(), bitmap2);
//
//        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{drawable1, drawable2});
//        imageView.setImageDrawable(layerDrawable);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String imageData = thingy.getImageData();
//        Assert.assertNotNull(imageData);
//        Assert.assertTrue(imageData.length() > 0);
//    }
//
//    @Test
//    public void testGetImageDataWithInsetDrawable() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        BitmapDrawable bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
//        InsetDrawable insetDrawable = new InsetDrawable(bitmapDrawable, 5);
//
//        imageView.setImageDrawable(insetDrawable);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String imageData = thingy.getImageData();
//        Assert.assertNotNull(imageData);
//        Assert.assertTrue(imageData.length() > 0);
//    }
//
//    @Test
//    public void testGetImageDataWithColorDrawable() {
//        ImageView imageView = new ImageView(context);
//        ColorDrawable colorDrawable = new ColorDrawable(Color.BLUE);
//        imageView.setImageDrawable(colorDrawable);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String imageData = thingy.getImageData();
//        // ColorDrawable should be converted to bitmap
//        Assert.assertNotNull(imageData);
//    }
//
//    @Test
//    public void testGetImageDataWithLayerDrawableNoLayers() {
//        ImageView imageView = new ImageView(context);
//        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{});
//        imageView.setImageDrawable(layerDrawable);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String imageData = thingy.getImageData();
//        // Empty LayerDrawable should still attempt to create bitmap
//        Assert.assertNotNull(imageData);
//    }
//
//    // ==================== IMAGE CACHING TESTS ====================
//
//    @Test
//    public void testImageCaching_CacheHit() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails1 = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy1 = new SessionReplayImageViewThingy(viewDetails1, imageView, agentConfiguration);
//        String imageData1 = thingy1.getImageData();
//
//        // Create second thingy with same image - should hit cache
//        ViewDetails viewDetails2 = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy2 = new SessionReplayImageViewThingy(viewDetails2, imageView, agentConfiguration);
//        String imageData2 = thingy2.getImageData();
//
//        Assert.assertNotNull(imageData1);
//        Assert.assertNotNull(imageData2);
//        Assert.assertEquals(imageData1, imageData2);
//    }
//
//    @Test
//    public void testClearImageCache() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//        Assert.assertNotNull(thingy.getImageData());
//
//        SessionReplayImageViewThingy.clearImageCache();
//
//        // Verify cache was cleared
//        String cacheStats = SessionReplayImageViewThingy.getCacheStats();
//        Assert.assertNotNull(cacheStats);
//        Assert.assertTrue(cacheStats.contains("Size: 0"));
//    }
//
//    @Test
//    public void testGetCacheStats() {
//        String cacheStats = SessionReplayImageViewThingy.getCacheStats();
//        Assert.assertNotNull(cacheStats);
//        Assert.assertTrue(cacheStats.contains("Size:"));
//        Assert.assertTrue(cacheStats.contains("Hits:"));
//        Assert.assertTrue(cacheStats.contains("Misses:"));
//    }
//
//    // ==================== MASKING TESTS ====================
//
//    @Test
//    public void testMaskAllImages_Enabled() {
//        sessionReplayConfiguration.setMaskAllImages(true);
//
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNull(thingy.getImageData());
//    }
//
//    @Test
//    public void testMaskAllImages_Disabled() {
//        sessionReplayConfiguration.setMaskAllImages(false);
//
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy.getImageData());
//    }
//
//    @Test
//    public void testMaskingWithPrivacyTag_NrMask() {
//        sessionReplayConfiguration.setMaskAllImages(false);
//
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//        imageView.setTag(R.id.newrelic_privacy, "nr-mask");
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNull(thingy.getImageData()); // Should be masked
//    }
//
//    @Test
//    public void testMaskingWithPrivacyTag_NrUnmask() {
//        sessionReplayConfiguration.setMaskAllImages(true);
//        sessionReplayConfiguration.setMode("custom");
//
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//        imageView.setTag(R.id.newrelic_privacy, "nr-unmask");
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy.getImageData()); // Should NOT be masked
//    }
//
//    @Test
//    public void testMaskingWithTag_NrMask() {
//        sessionReplayConfiguration.setMaskAllImages(false);
//
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//        imageView.setTag("nr-mask");
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNull(thingy.getImageData()); // Should be masked
//    }
//
//    @Test
//    public void testMaskingWithTag_NrUnmask() {
//        sessionReplayConfiguration.setMaskAllImages(true);
//        sessionReplayConfiguration.setMode("custom");
//
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//        imageView.setTag("nr-unmask");
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy.getImageData()); // Should NOT be masked
//    }
//
//    // ==================== SCALE TYPE TESTS ====================
//
//    @Test
//    public void testScaleType_FitXY() {
//        ImageView imageView = new ImageView(context);
//        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateInlineCss();
//        Assert.assertTrue(css.contains("background-size: 100% 100%"));
//    }
//
//    @Test
//    public void testScaleType_CenterCrop() {
//        ImageView imageView = new ImageView(context);
//        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateInlineCss();
//        Assert.assertTrue(css.contains("background-size: cover"));
//    }
//
//    @Test
//    public void testScaleType_FitCenter() {
//        ImageView imageView = new ImageView(context);
//        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateInlineCss();
//        Assert.assertTrue(css.contains("background-size: contain"));
//    }
//
//    @Test
//    public void testScaleType_CenterInside() {
//        ImageView imageView = new ImageView(context);
//        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateInlineCss();
//        Assert.assertTrue(css.contains("background-size: contain"));
//    }
//
//    @Test
//    public void testScaleType_Center() {
//        ImageView imageView = new ImageView(context);
//        imageView.setScaleType(ImageView.ScaleType.CENTER);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateInlineCss();
//        Assert.assertTrue(css.contains("background-size: auto"));
//    }
//
//    // ==================== CSS GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateCssDescription() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateCssDescription();
//        Assert.assertNotNull(css);
//        Assert.assertTrue(css.contains("background-color:"));
//    }
//
//    @Test
//    public void testGenerateInlineCss() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateInlineCss();
//        Assert.assertNotNull(css);
//        Assert.assertTrue(css.contains("background-color:"));
//        Assert.assertTrue(css.contains("background-size:"));
//        Assert.assertTrue(css.contains("background-repeat: no-repeat"));
//        Assert.assertTrue(css.contains("background-position: center"));
//    }
//
//    @Test
//    public void testGenerateInlineCssWithImageData() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateInlineCss();
//        Assert.assertTrue(css.contains("background-image: url("));
//    }
//
//    @Test
//    public void testGenerateInlineCssWithoutImageData() {
//        ImageView imageView = new ImageView(context);
//        // No image set
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String css = thingy.generateInlineCss();
//        Assert.assertFalse(css.contains("background-image:"));
//    }
//
//    @Test
//    public void testGetImageDataUrl() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String dataUrl = thingy.getImageDataUrl();
//        Assert.assertNotNull(dataUrl);
//        Assert.assertTrue(dataUrl.startsWith("data:image/"));
//    }
//
//    // ==================== RRWEB NODE GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateRRWebNode() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node);
//        Assert.assertEquals(RRWebElementNode.TAG_TYPE_DIV, node.getTagName());
//        Assert.assertNotNull(node.getAttributes());
//        Assert.assertNotNull(node.getChildNodes());
//        Assert.assertTrue(node.getChildNodes().isEmpty());
//    }
//
//    // ==================== DIFF GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateDifferences_WithSameImage() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails1 = new ViewDetails(imageView);
//        ViewDetails viewDetails2 = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy1 = new SessionReplayImageViewThingy(viewDetails1, imageView, agentConfiguration);
//        SessionReplayImageViewThingy thingy2 = new SessionReplayImageViewThingy(viewDetails2, imageView, agentConfiguration);
//
//        List<MutationRecord> diffs = thingy1.generateDifferences(thingy2);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertEquals(1, diffs.size());
//    }
//
//    @Test
//    public void testGenerateDifferences_WithDifferentImages() {
//        ImageView imageView1 = new ImageView(context);
//        Bitmap bitmap1 = createTestBitmap(10, 10);
//        imageView1.setImageBitmap(bitmap1);
//
//        ImageView imageView2 = new ImageView(context);
//        Bitmap bitmap2 = createTestBitmap(20, 20);
//        imageView2.setImageBitmap(bitmap2);
//
//        ViewDetails viewDetails1 = new ViewDetails(imageView1);
//        ViewDetails viewDetails2 = new ViewDetails(imageView2);
//
//        SessionReplayImageViewThingy thingy1 = new SessionReplayImageViewThingy(viewDetails1, imageView1, agentConfiguration);
//        SessionReplayImageViewThingy thingy2 = new SessionReplayImageViewThingy(viewDetails2, imageView2, agentConfiguration);
//
//        List<MutationRecord> diffs = thingy1.generateDifferences(thingy2);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertFalse(diffs.isEmpty());
//    }
//
//    @Test
//    public void testGenerateDifferences_WithDifferentType() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        // Create a different type
//        SessionReplayViewThingyInterface otherType = new SessionReplayViewThingy(viewDetails);
//
//        List<MutationRecord> diffs = thingy.generateDifferences(otherType);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertTrue(diffs.isEmpty());
//    }
//
//    @Test
//    public void testGenerateDifferences_WithNullOther() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        List<MutationRecord> diffs = thingy.generateDifferences(null);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertTrue(diffs.isEmpty());
//    }
//
//    // ==================== ADD RECORD GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateAdditionNodes() {
//        ImageView imageView = new ImageView(context);
//        Bitmap bitmap = createTestBitmap(10, 10);
//        imageView.setImageBitmap(bitmap);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        int parentId = 123;
//        List<RRWebMutationData.AddRecord> addRecords = thingy.generateAdditionNodes(parentId);
//
//        Assert.assertNotNull(addRecords);
//        Assert.assertEquals(1, addRecords.size());
//        Assert.assertEquals(parentId, addRecords.get(0).getParentId());
//    }
//
//    // ==================== GETTER TESTS ====================
//
//    @Test
//    public void testGetViewDetails() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertEquals(viewDetails, thingy.getViewDetails());
//    }
//
//    @Test
//    public void testShouldRecordSubviews() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        // ImageView should not record subviews
//        Assert.assertFalse(thingy.shouldRecordSubviews());
//    }
//
//    @Test
//    public void testGetSubviews() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy.getSubviews());
//        Assert.assertTrue(thingy.getSubviews().isEmpty());
//    }
//
//    @Test
//    public void testGetViewId() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertEquals(viewDetails.viewId, thingy.getViewId());
//    }
//
//    @Test
//    public void testGetParentViewId() {
//        FrameLayout parent = new FrameLayout(context);
//        parent.setTag(NewRelicIdGenerator.NR_ID_TAG, 999);
//
//        ImageView imageView = new ImageView(context);
//        parent.addView(imageView);
//
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertEquals(viewDetails.parentId, thingy.getParentViewId());
//    }
//
//    @Test
//    public void testGetCssSelector() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        String cssSelector = thingy.getCssSelector();
//        Assert.assertNotNull(cssSelector);
//        Assert.assertEquals(viewDetails.getCssSelector(), cssSelector);
//    }
//
//    // ==================== CHANGE DETECTION TESTS ====================
//
//    @Test
//    public void testHasChanged_WithNull() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertTrue(thingy.hasChanged(null));
//    }
//
//    @Test
//    public void testHasChanged_WithDifferentType() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//        SessionReplayViewThingy otherType = new SessionReplayViewThingy(viewDetails);
//
//        Assert.assertTrue(thingy.hasChanged(otherType));
//    }
//
//    @Test
//    public void testHasChanged_WithSameInstance() {
//        ImageView imageView = new ImageView(context);
//        ViewDetails viewDetails = new ViewDetails(imageView);
//
//        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(viewDetails, imageView, agentConfiguration);
//
//        Assert.assertFalse(thingy.hasChanged(thingy));
//    }
//
//    @Test
//    public void testHasChanged_WithDifferentInstances() {
//        ImageView imageView1 = new ImageView(context);
//        Bitmap bitmap1 = createTestBitmap(10, 10);
//        imageView1.setImageBitmap(bitmap1);
//        ViewDetails viewDetails1 = new ViewDetails(imageView1);
//
//        ImageView imageView2 = new ImageView(context);
//        Bitmap bitmap2 = createTestBitmap(20, 20);
//        imageView2.setImageBitmap(bitmap2);
//        ViewDetails viewDetails2 = new ViewDetails(imageView2);
//
//        SessionReplayImageViewThingy thingy1 = new SessionReplayImageViewThingy(viewDetails1, imageView1, agentConfiguration);
//        SessionReplayImageViewThingy thingy2 = new SessionReplayImageViewThingy(viewDetails2, imageView2, agentConfiguration);
//
//        Assert.assertTrue(thingy1.hasChanged(thingy2));
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    private Bitmap createTestBitmap(int width, int height) {
//        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//    }
//}