///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.view.View;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebIncrementalEvent;
//import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
//import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
//import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
//import com.newrelic.agent.android.sessionReplay.models.RRWebFullSnapshotEvent;
//import com.newrelic.agent.android.sessionReplay.models.RRWebMetaEvent;
//
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayProcessorTest {
//
//    private Context context;
//
//    @Before
//    public void setUp() {
//        context = ApplicationProvider.getApplicationContext();
//    }
//
//    // ==================== CONSTRUCTOR/STATE TESTS ====================
//
//    @Test
//    public void testConstructor() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        Assert.assertNotNull(processor);
//    }
//
//    @Test
//    public void testConstants() {
//        Assert.assertEquals(2, SessionReplayProcessor.RRWEB_TYPE_FULL_SNAPSHOT);
//        Assert.assertEquals(3, SessionReplayProcessor.RRWEB_TYPE_INCREMENTAL_SNAPSHOT);
//    }
//
//    // ==================== PROCESS FRAMES - FULL SNAPSHOT TESTS ====================
//
//    @Test
//    public void testProcessFrames_SingleFrame_TakesFullSnapshot() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(1000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame), true);
//
//        Assert.assertNotNull(events);
//        Assert.assertEquals(2, events.size()); // Meta + Full snapshot
//
//        Assert.assertTrue(events.get(0) instanceof RRWebMetaEvent);
//        Assert.assertTrue(events.get(1) instanceof RRWebFullSnapshotEvent);
//    }
//
//    @Test
//    public void testProcessFrames_FirstFrameAlwaysFullSnapshot() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(1000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame), false);
//
//        Assert.assertNotNull(events);
//        Assert.assertEquals(2, events.size()); // Meta + Full snapshot
//
//        Assert.assertTrue(events.get(0) instanceof RRWebMetaEvent);
//        Assert.assertTrue(events.get(1) instanceof RRWebFullSnapshotEvent);
//    }
//
//    @Test
//    public void testProcessFrames_ForceFullSnapshot() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame1 = createTestFrame(1000L, 1080, 1920);
//        SessionReplayFrame frame2 = createTestFrame(2000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame1, frame2), true);
//
//        Assert.assertNotNull(events);
//        Assert.assertEquals(4, events.size()); // 2 Meta + 2 Full snapshots
//
//        // All should be full snapshots
//        Assert.assertTrue(events.get(0) instanceof RRWebMetaEvent);
//        Assert.assertTrue(events.get(1) instanceof RRWebFullSnapshotEvent);
//        Assert.assertTrue(events.get(2) instanceof RRWebMetaEvent);
//        Assert.assertTrue(events.get(3) instanceof RRWebFullSnapshotEvent);
//    }
//
//    // ==================== PROCESS FRAMES - INCREMENTAL SNAPSHOT TESTS ====================
//
//    @Test
//    public void testProcessFrames_TwoFrames_SecondIsIncremental() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        View view1 = new View(context);
//        ViewDetails details1 = new ViewDetails(view1);
//        SessionReplayViewThingy thingy1 = new SessionReplayViewThingy(details1);
//        SessionReplayFrame frame1 = new SessionReplayFrame(thingy1, 1000L, 1080, 1920);
//
//        // Same root view ID
//        View view2 = new View(context);
//        view2.setTag(NewRelicIdGenerator.NR_ID_TAG, details1.viewId); // Force same ID
//        ViewDetails details2 = new ViewDetails(view2);
//        SessionReplayViewThingy thingy2 = new SessionReplayViewThingy(details2);
//        SessionReplayFrame frame2 = new SessionReplayFrame(thingy2, 2000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame1, frame2), false);
//
//        Assert.assertNotNull(events);
//        Assert.assertTrue(events.size() >= 3); // Meta + Full + Incremental
//
//        Assert.assertTrue(events.get(0) instanceof RRWebMetaEvent);
//        Assert.assertTrue(events.get(1) instanceof RRWebFullSnapshotEvent);
//        Assert.assertTrue(events.get(2) instanceof RRWebIncrementalEvent);
//    }
//
//    @Test
//    public void testProcessFrames_DimensionChange_TakesFullSnapshot() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame1 = createTestFrame(1000L, 1080, 1920);
//        SessionReplayFrame frame2 = createTestFrame(2000L, 1920, 1080); // Different dimensions
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame1, frame2), false);
//
//        Assert.assertNotNull(events);
//        // Should have 2 full snapshots due to dimension change
//        Assert.assertEquals(4, events.size()); // 2 Meta + 2 Full
//
//        Assert.assertTrue(events.get(2) instanceof RRWebMetaEvent);
//        Assert.assertTrue(events.get(3) instanceof RRWebFullSnapshotEvent);
//    }
//
//    @Test
//    public void testProcessFrames_RootViewIdChange_TakesFullSnapshot() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame1 = createTestFrame(1000L, 1080, 1920);
//        SessionReplayFrame frame2 = createTestFrame(2000L, 1080, 1920);
//
//        // Different root view IDs will trigger full snapshot
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame1, frame2), false);
//
//        Assert.assertNotNull(events);
//        // Should have 2 full snapshots due to root ID change
//        Assert.assertEquals(4, events.size());
//    }
//
//    @Test
//    public void testProcessFrames_EmptyList() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        List<RRWebEvent> events = processor.processFrames(new ArrayList<>(), false);
//
//        Assert.assertNotNull(events);
//        Assert.assertTrue(events.isEmpty());
//    }
//
//    // ==================== PROCESS FULL FRAME TESTS ====================
//
//    @Test
//    public void testProcessFullFrame() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(1000L, 1080, 1920);
//
//        RRWebFullSnapshotEvent event = processor.processFullFrame(frame);
//
//        Assert.assertNotNull(event);
//        Assert.assertEquals(1000L, event.getTimestamp());
//        Assert.assertNotNull(event.getData());
//        Assert.assertNotNull(event.getData().getNode());
//    }
//
//    @Test
//    public void testProcessFullFrame_GeneratesHtmlStructure() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(1000L, 1080, 1920);
//
//        RRWebFullSnapshotEvent event = processor.processFullFrame(frame);
//
//        Assert.assertNotNull(event.getData().getNode());
//        Assert.assertNotNull(event.getData().getNode().getChildNodes());
//        Assert.assertFalse(event.getData().getNode().getChildNodes().isEmpty());
//
//        // Should have HTML node as first child
//        RRWebElementNode htmlNode = (RRWebElementNode) event.getData().getNode().getChildNodes().get(0);
//        Assert.assertEquals(RRWebElementNode.TAG_TYPE_HTML, htmlNode.getTagName());
//    }
//
//    @Test
//    public void testProcessFullFrame_GeneratesHeadAndBody() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(1000L, 1080, 1920);
//
//        RRWebFullSnapshotEvent event = processor.processFullFrame(frame);
//
//        RRWebElementNode htmlNode = (RRWebElementNode) event.getData().getNode().getChildNodes().get(0);
//        Assert.assertEquals(2, htmlNode.getChildNodes().size());
//
//        RRWebElementNode headNode = (RRWebElementNode) htmlNode.getChildNodes().get(0);
//        Assert.assertEquals(RRWebElementNode.TAG_TYPE_HEAD, headNode.getTagName());
//
//        RRWebElementNode bodyNode = (RRWebElementNode) htmlNode.getChildNodes().get(1);
//        Assert.assertEquals(RRWebElementNode.TAG_TYPE_BODY, bodyNode.getTagName());
//    }
//
//    @Test
//    public void testProcessFullFrame_GeneratesCssInHead() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(1000L, 1080, 1920);
//
//        RRWebFullSnapshotEvent event = processor.processFullFrame(frame);
//
//        RRWebElementNode htmlNode = (RRWebElementNode) event.getData().getNode().getChildNodes().get(0);
//        RRWebElementNode headNode = (RRWebElementNode) htmlNode.getChildNodes().get(0);
//
//        Assert.assertFalse(headNode.getChildNodes().isEmpty());
//        RRWebElementNode styleNode = (RRWebElementNode) headNode.getChildNodes().get(0);
//        Assert.assertEquals(RRWebElementNode.TAG_TYPE_STYLE, styleNode.getTagName());
//    }
//
//    @Test
//    public void testProcessFullFrame_WithNestedViews() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        View parentView = new View(context);
//        ViewDetails parentDetails = new ViewDetails(parentView);
//        SessionReplayViewThingy parentThingy = new SessionReplayViewThingy(parentDetails);
//
//        View childView = new View(context);
//        ViewDetails childDetails = new ViewDetails(childView);
//        SessionReplayViewThingy childThingy = new SessionReplayViewThingy(childDetails);
//
//        parentThingy.setSubviews(Arrays.asList(childThingy));
//
//        SessionReplayFrame frame = new SessionReplayFrame(parentThingy, 1000L, 1080, 1920);
//
//        RRWebFullSnapshotEvent event = processor.processFullFrame(frame);
//
//        Assert.assertNotNull(event);
//        Assert.assertNotNull(event.getData());
//    }
//
//    // ==================== CREATE META EVENT TESTS ====================
//
//    @Test
//    public void testCreateMetaEvent() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(1234L, 1080, 1920);
//
//        RRWebMetaEvent metaEvent = processor.createMetaEvent(frame);
//
//        Assert.assertNotNull(metaEvent);
//        Assert.assertEquals(1234L, metaEvent.getTimestamp());
//        Assert.assertNotNull(metaEvent.getData());
//        Assert.assertEquals("https://newrelic.com", metaEvent.getData().getHref());
//        Assert.assertEquals(1080, metaEvent.getData().getWidth());
//        Assert.assertEquals(1920, metaEvent.getData().getHeight());
//    }
//
//    @Test
//    public void testCreateMetaEvent_WithDifferentDimensions() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(5000L, 800, 600);
//
//        RRWebMetaEvent metaEvent = processor.createMetaEvent(frame);
//
//        Assert.assertEquals(800, metaEvent.getData().getWidth());
//        Assert.assertEquals(600, metaEvent.getData().getHeight());
//    }
//
//    @Test
//    public void testCreateMetaEvent_PreservesTimestamp() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        long[] timestamps = {0L, 1L, 1000L, 999999999L, Long.MAX_VALUE};
//
//        for (long timestamp : timestamps) {
//            SessionReplayFrame frame = createTestFrame(timestamp, 1080, 1920);
//            RRWebMetaEvent metaEvent = processor.createMetaEvent(frame);
//            Assert.assertEquals(timestamp, metaEvent.getTimestamp());
//        }
//    }
//
//    // ==================== ON NEW SCREEN TESTS ====================
//
//    @Test
//    public void testOnNewScreen_ResetsLastFrame() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        // Process first frame
//        SessionReplayFrame frame1 = createTestFrame(1000L, 1080, 1920);
//        processor.processFrames(Arrays.asList(frame1), false);
//
//        // Call onNewScreen
//        processor.onNewScreen();
//
//        // Next frame should be full snapshot again
//        SessionReplayFrame frame2 = createTestFrame(2000L, 1080, 1920);
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame2), false);
//
//        // Should be full snapshot (Meta + Full)
//        Assert.assertEquals(2, events.size());
//        Assert.assertTrue(events.get(0) instanceof RRWebMetaEvent);
//        Assert.assertTrue(events.get(1) instanceof RRWebFullSnapshotEvent);
//    }
//
//    @Test
//    public void testOnNewScreen_MultipleCallsSafe() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        processor.onNewScreen();
//        processor.onNewScreen();
//        processor.onNewScreen();
//
//        // Should not cause issues
//        SessionReplayFrame frame = createTestFrame(1000L, 1080, 1920);
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame), false);
//
//        Assert.assertNotNull(events);
//    }
//
//    // ==================== FLATTEN TREE TESTS ====================
//
//    @Test
//    public void testFlattenTree_SingleNode() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        View view = new View(context);
//        ViewDetails details = new ViewDetails(view);
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(details);
//
//        SessionReplayFrame frame = new SessionReplayFrame(thingy, 1000L, 1080, 1920);
//
//        // Test indirectly through processFrames which uses flattenTree
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame), false);
//
//        Assert.assertNotNull(events);
//        Assert.assertTrue(events.size() >= 2);
//    }
//
//    @Test
//    public void testFlattenTree_TwoLevels() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        View parentView = new View(context);
//        ViewDetails parentDetails = new ViewDetails(parentView);
//        SessionReplayViewThingy parentThingy = new SessionReplayViewThingy(parentDetails);
//
//        View childView = new View(context);
//        ViewDetails childDetails = new ViewDetails(childView);
//        SessionReplayViewThingy childThingy = new SessionReplayViewThingy(childDetails);
//
//        parentThingy.setSubviews(Arrays.asList(childThingy));
//
//        SessionReplayFrame frame = new SessionReplayFrame(parentThingy, 1000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame), false);
//
//        Assert.assertNotNull(events);
//    }
//
//    @Test
//    public void testFlattenTree_DeeplyNested() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        // Create 10-level deep hierarchy
//        SessionReplayViewThingy root = null;
//        SessionReplayViewThingy current = null;
//
//        for (int i = 0; i < 10; i++) {
//            View view = new View(context);
//            ViewDetails details = new ViewDetails(view);
//            SessionReplayViewThingy thingy = new SessionReplayViewThingy(details);
//
//            if (root == null) {
//                root = thingy;
//                current = thingy;
//            } else {
//                current.setSubviews(Arrays.asList(thingy));
//                current = thingy;
//            }
//        }
//
//        SessionReplayFrame frame = new SessionReplayFrame(root, 1000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame), false);
//
//        Assert.assertNotNull(events);
//    }
//
//    // ==================== MULTIPLE FRAMES TESTS ====================
//
//    @Test
//    public void testProcessFrames_MultipleFramesSameRoot() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        View view = new View(context);
//        ViewDetails details = new ViewDetails(view);
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(details);
//
//        SessionReplayFrame frame1 = new SessionReplayFrame(thingy, 1000L, 1080, 1920);
//
//        // Second frame with same root ID
//        View view2 = new View(context);
//        view2.setTag(NewRelicIdGenerator.NR_ID_TAG, details.viewId);
//        ViewDetails details2 = new ViewDetails(view2);
//        SessionReplayViewThingy thingy2 = new SessionReplayViewThingy(details2);
//        SessionReplayFrame frame2 = new SessionReplayFrame(thingy2, 2000L, 1080, 1920);
//
//        SessionReplayFrame frame3 = new SessionReplayFrame(thingy2, 3000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame1, frame2, frame3), false);
//
//        Assert.assertNotNull(events);
//        Assert.assertTrue(events.size() >= 4); // At least Meta + Full + 2 Incrementals
//    }
//
//    @Test
//    public void testProcessFrames_AlternatingDimensions() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        SessionReplayFrame frame1 = createTestFrame(1000L, 1080, 1920);
//        SessionReplayFrame frame2 = createTestFrame(2000L, 1920, 1080);
//        SessionReplayFrame frame3 = createTestFrame(3000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame1, frame2, frame3), false);
//
//        Assert.assertNotNull(events);
//        // Each dimension change should trigger full snapshot
//        Assert.assertTrue(events.size() >= 6); // 3 Meta + 3 Full
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testProcessFrames_NullFrameHandling() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        // Should handle gracefully or throw appropriate exception
//        try {
//            List<SessionReplayFrame> frames = new ArrayList<>();
//            frames.add(null);
//            processor.processFrames(frames, false);
//        } catch (NullPointerException e) {
//            // Expected
//        }
//    }
//
//    @Test
//    public void testProcessFrames_ZeroDimensions() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(1000L, 0, 0);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame), false);
//
//        Assert.assertNotNull(events);
//        Assert.assertTrue(events.size() >= 2);
//
//        RRWebMetaEvent metaEvent = (RRWebMetaEvent) events.get(0);
//        Assert.assertEquals(0, metaEvent.getData().getWidth());
//        Assert.assertEquals(0, metaEvent.getData().getHeight());
//    }
//
//    @Test
//    public void testProcessFrames_NegativeTimestamp() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//        SessionReplayFrame frame = createTestFrame(-1000L, 1080, 1920);
//
//        List<RRWebEvent> events = processor.processFrames(Arrays.asList(frame), false);
//
//        Assert.assertNotNull(events);
//    }
//
//    @Test
//    public void testProcessFullFrame_WithEmptySubviews() {
//        SessionReplayProcessor processor = new SessionReplayProcessor();
//
//        View view = new View(context);
//        ViewDetails details = new ViewDetails(view);
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(details);
//        thingy.setSubviews(new ArrayList<>());
//
//        SessionReplayFrame frame = new SessionReplayFrame(thingy, 1000L, 1080, 1920);
//
//        RRWebFullSnapshotEvent event = processor.processFullFrame(frame);
//
//        Assert.assertNotNull(event);
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    private SessionReplayFrame createTestFrame(long timestamp, int width, int height) {
//        View view = new View(context);
//        ViewDetails details = new ViewDetails(view);
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(details);
//        return new SessionReplayFrame(thingy, timestamp, width, height);
//    }
//}