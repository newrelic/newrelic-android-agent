///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import com.newrelic.agent.android.sessionReplay.models.RRWebRRWebTouchUpDownData;
//import com.newrelic.agent.android.sessionReplay.models.RRWebTouch;
//import com.newrelic.agent.android.sessionReplay.models.RRWebTouchMoveData;
//import com.newrelic.agent.android.sessionReplay.models.RecordedTouchData;
//
//import org.junit.Assert;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//import java.util.ArrayList;
//
//@RunWith(RobolectricTestRunner.class)
//public class TouchTrackerTest {
//
//    // ==================== CONSTRUCTOR TESTS ====================
//
//    @Test
//    public void testConstructor_WithStartTouch() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        Assert.assertNotNull(tracker);
//    }
//
//    @Test
//    public void testConstructor_WithNullStartTouch() {
//        try {
//            TouchTracker tracker = new TouchTracker(null);
//            // May or may not throw depending on implementation
//        } catch (NullPointerException e) {
//            // Expected if null is not allowed
//        }
//    }
//
//    // ==================== ADD MOVE TOUCH TESTS ====================
//
//    @Test
//    public void testAddMoveTouch_SingleMove() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData moveTouch = new RecordedTouchData(2, 100, 60.0f, 85.0f, 1100L);
//        tracker.addMoveTouch(moveTouch);
//
//        // No exception should be thrown
//        Assert.assertNotNull(tracker);
//    }
//
//    @Test
//    public void testAddMoveTouch_MultipleMoves() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        for (int i = 1; i <= 10; i++) {
//            RecordedTouchData moveTouch = new RecordedTouchData(2, 100, 50.0f + i, 75.0f + i, 1000L + i * 100);
//            tracker.addMoveTouch(moveTouch);
//        }
//
//        Assert.assertNotNull(tracker);
//    }
//
//    @Test
//    public void testAddMoveTouch_NoMoves() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        // Don't add any move touches
//        Assert.assertNotNull(tracker);
//    }
//
//    // ==================== ADD END TOUCH TESTS ====================
//
//    @Test
//    public void testAddEndTouch() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 60.0f, 85.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        Assert.assertNotNull(tracker);
//    }
//
//    @Test
//    public void testAddEndTouch_Overwrite() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch1 = new RecordedTouchData(1, 100, 60.0f, 85.0f, 1200L);
//        tracker.addEndTouch(endTouch1);
//
//        RecordedTouchData endTouch2 = new RecordedTouchData(1, 100, 70.0f, 95.0f, 1300L);
//        tracker.addEndTouch(endTouch2);
//
//        // Second end touch should overwrite the first
//        Assert.assertNotNull(tracker);
//    }
//
//    // ==================== PROCESS TOUCH DATA TESTS ====================
//
//    @Test
//    public void testProcessTouchData_SimpleDownUp() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 50.0f, 75.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        Assert.assertNotNull(touches);
//        Assert.assertEquals(2, touches.size()); // Down + Up
//
//        // Check down touch
//        RRWebTouch downTouchEvent = touches.get(0);
//        Assert.assertEquals(1000L, downTouchEvent.getTimestamp());
//        Assert.assertEquals(3, downTouchEvent.getType());
//        Assert.assertTrue(downTouchEvent.getData() instanceof RRWebRRWebTouchUpDownData);
//
//        RRWebRRWebTouchUpDownData downData = (RRWebRRWebTouchUpDownData) downTouchEvent.getData();
//        Assert.assertEquals(2, downData.getSource());
//        Assert.assertEquals(7, downData.getTouchType()); // TouchDown
//        Assert.assertEquals(100, downData.getId());
//        Assert.assertEquals(50.0f, downData.getX(), 0.01f);
//        Assert.assertEquals(75.0f, downData.getY(), 0.01f);
//
//        // Check up touch
//        RRWebTouch upTouchEvent = touches.get(1);
//        Assert.assertEquals(1200L, upTouchEvent.getTimestamp());
//        Assert.assertEquals(3, upTouchEvent.getType());
//        Assert.assertTrue(upTouchEvent.getData() instanceof RRWebRRWebTouchUpDownData);
//
//        RRWebRRWebTouchUpDownData upData = (RRWebRRWebTouchUpDownData) upTouchEvent.getData();
//        Assert.assertEquals(2, upData.getSource());
//        Assert.assertEquals(9, upData.getTouchType()); // TouchUp
//        Assert.assertEquals(100, upData.getId());
//        Assert.assertEquals(50.0f, upData.getX(), 0.01f);
//        Assert.assertEquals(75.0f, upData.getY(), 0.01f);
//    }
//
//    @Test
//    public void testProcessTouchData_WithSingleMove() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData moveTouch = new RecordedTouchData(2, 100, 60.0f, 85.0f, 1100L);
//        tracker.addMoveTouch(moveTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 70.0f, 95.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        Assert.assertNotNull(touches);
//        Assert.assertEquals(3, touches.size()); // Down + Move + Up
//
//        // Check move touch
//        RRWebTouch moveTouchEvent = touches.get(1);
//        Assert.assertEquals(1100L, moveTouchEvent.getTimestamp());
//        Assert.assertTrue(moveTouchEvent.getData() instanceof RRWebTouchMoveData);
//
//        RRWebTouchMoveData moveData = (RRWebTouchMoveData) moveTouchEvent.getData();
//        Assert.assertEquals(1, moveData.getSource());
//        Assert.assertNotNull(moveData.getPositions());
//        Assert.assertEquals(1, moveData.getPositions().size());
//
//        RRWebTouchMoveData.Position position = moveData.getPositions().get(0);
//        Assert.assertEquals(100, position.getId());
//        Assert.assertEquals(60.0f, position.getX(), 0.01f);
//        Assert.assertEquals(85.0f, position.getY(), 0.01f);
//    }
//
//    @Test
//    public void testProcessTouchData_WithMultipleMoves() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData move1 = new RecordedTouchData(2, 100, 55.0f, 80.0f, 1050L);
//        RecordedTouchData move2 = new RecordedTouchData(2, 100, 60.0f, 85.0f, 1100L);
//        RecordedTouchData move3 = new RecordedTouchData(2, 100, 65.0f, 90.0f, 1150L);
//
//        tracker.addMoveTouch(move1);
//        tracker.addMoveTouch(move2);
//        tracker.addMoveTouch(move3);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 70.0f, 95.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        Assert.assertNotNull(touches);
//        Assert.assertEquals(3, touches.size()); // Down + Move + Up
//
//        // Check move touch has all positions
//        RRWebTouch moveTouchEvent = touches.get(1);
//        RRWebTouchMoveData moveData = (RRWebTouchMoveData) moveTouchEvent.getData();
//        Assert.assertEquals(3, moveData.getPositions().size());
//
//        // Verify positions
//        Assert.assertEquals(55.0f, moveData.getPositions().get(0).getX(), 0.01f);
//        Assert.assertEquals(60.0f, moveData.getPositions().get(1).getX(), 0.01f);
//        Assert.assertEquals(65.0f, moveData.getPositions().get(2).getX(), 0.01f);
//    }
//
//    @Test
//    public void testProcessTouchData_TimestampCalculationInMoves() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData move1 = new RecordedTouchData(2, 100, 55.0f, 80.0f, 1050L);
//        RecordedTouchData move2 = new RecordedTouchData(2, 100, 60.0f, 85.0f, 1100L);
//
//        tracker.addMoveTouch(move1);
//        tracker.addMoveTouch(move2);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 70.0f, 95.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        RRWebTouch moveTouchEvent = touches.get(1);
//        RRWebTouchMoveData moveData = (RRWebTouchMoveData) moveTouchEvent.getData();
//
//        // Timestamp should be the last move's timestamp
//        Assert.assertEquals(1100L, moveTouchEvent.getTimestamp());
//
//        // Time offsets should be relative to last timestamp
//        long expectedOffset1 = 1050L - 1100L; // -50
//        long expectedOffset2 = 1100L - 1100L; // 0
//
//        Assert.assertEquals(expectedOffset1, moveData.getPositions().get(0).getTimeOffset());
//        Assert.assertEquals(expectedOffset2, moveData.getPositions().get(1).getTimeOffset());
//    }
//
//    // ==================== TOUCH DATA FORMAT TESTS ====================
//
//    @Test
//    public void testProcessTouchData_TouchDownType() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 50.0f, 75.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        RRWebRRWebTouchUpDownData downData = (RRWebRRWebTouchUpDownData) touches.get(0).getData();
//        Assert.assertEquals(7, downData.getTouchType()); // TouchDown type
//    }
//
//    @Test
//    public void testProcessTouchData_TouchUpType() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 50.0f, 75.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        RRWebRRWebTouchUpDownData upData = (RRWebRRWebTouchUpDownData) touches.get(1).getData();
//        Assert.assertEquals(9, upData.getTouchType()); // TouchUp type
//    }
//
//    @Test
//    public void testProcessTouchData_SourceValues() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData moveTouch = new RecordedTouchData(2, 100, 60.0f, 85.0f, 1100L);
//        tracker.addMoveTouch(moveTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 70.0f, 95.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        // Down and Up should have source = 2
//        RRWebRRWebTouchUpDownData downData = (RRWebRRWebTouchUpDownData) touches.get(0).getData();
//        Assert.assertEquals(2, downData.getSource());
//
//        RRWebRRWebTouchUpDownData upData = (RRWebRRWebTouchUpDownData) touches.get(2).getData();
//        Assert.assertEquals(2, upData.getSource());
//
//        // Move should have source = 1
//        RRWebTouchMoveData moveData = (RRWebTouchMoveData) touches.get(1).getData();
//        Assert.assertEquals(1, moveData.getSource());
//    }
//
//    @Test
//    public void testProcessTouchData_EventType() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 50.0f, 75.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        // All events should have type = 3
//        for (RRWebTouch touch : touches) {
//            Assert.assertEquals(3, touch.getType());
//        }
//    }
//
//    // ==================== COORDINATE TESTS ====================
//
//    @Test
//    public void testProcessTouchData_PreservesCoordinates() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 123.45f, 678.90f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 234.56f, 789.01f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        RRWebRRWebTouchUpDownData downData = (RRWebRRWebTouchUpDownData) touches.get(0).getData();
//        Assert.assertEquals(123.45f, downData.getX(), 0.01f);
//        Assert.assertEquals(678.90f, downData.getY(), 0.01f);
//
//        RRWebRRWebTouchUpDownData upData = (RRWebRRWebTouchUpDownData) touches.get(1).getData();
//        Assert.assertEquals(234.56f, upData.getX(), 0.01f);
//        Assert.assertEquals(789.01f, upData.getY(), 0.01f);
//    }
//
//    @Test
//    public void testProcessTouchData_NegativeCoordinates() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, -10.0f, -20.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, -5.0f, -15.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        RRWebRRWebTouchUpDownData downData = (RRWebRRWebTouchUpDownData) touches.get(0).getData();
//        Assert.assertEquals(-10.0f, downData.getX(), 0.01f);
//        Assert.assertEquals(-20.0f, downData.getY(), 0.01f);
//    }
//
//    @Test
//    public void testProcessTouchData_ZeroCoordinates() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 0.0f, 0.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 0.0f, 0.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        RRWebRRWebTouchUpDownData downData = (RRWebRRWebTouchUpDownData) touches.get(0).getData();
//        Assert.assertEquals(0.0f, downData.getX(), 0.01f);
//        Assert.assertEquals(0.0f, downData.getY(), 0.01f);
//    }
//
//    // ==================== VIEW ID TESTS ====================
//
//    @Test
//    public void testProcessTouchData_PreservesViewId() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 12345, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 12345, 50.0f, 75.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        RRWebRRWebTouchUpDownData downData = (RRWebRRWebTouchUpDownData) touches.get(0).getData();
//        Assert.assertEquals(12345, downData.getId());
//
//        RRWebRRWebTouchUpDownData upData = (RRWebRRWebTouchUpDownData) touches.get(1).getData();
//        Assert.assertEquals(12345, upData.getId());
//    }
//
//    @Test
//    public void testProcessTouchData_DifferentViewIdsDuringGesture() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        // Move to different view
//        RecordedTouchData moveTouch = new RecordedTouchData(2, 200, 60.0f, 85.0f, 1100L);
//        tracker.addMoveTouch(moveTouch);
//
//        // End on yet another view
//        RecordedTouchData endTouch = new RecordedTouchData(1, 300, 70.0f, 95.0f, 1200L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        RRWebRRWebTouchUpDownData downData = (RRWebRRWebTouchUpDownData) touches.get(0).getData();
//        Assert.assertEquals(100, downData.getId());
//
//        RRWebTouchMoveData moveData = (RRWebTouchMoveData) touches.get(1).getData();
//        Assert.assertEquals(200, moveData.getPositions().get(0).getId());
//
//        RRWebRRWebTouchUpDownData upData = (RRWebRRWebTouchUpDownData) touches.get(2).getData();
//        Assert.assertEquals(300, upData.getId());
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testProcessTouchData_VeryLongGesture() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        // Add 100 move touches
//        for (int i = 1; i <= 100; i++) {
//            RecordedTouchData moveTouch = new RecordedTouchData(2, 100, 50.0f + i, 75.0f + i, 1000L + i * 10);
//            tracker.addMoveTouch(moveTouch);
//        }
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 200.0f, 200.0f, 3000L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        Assert.assertEquals(3, touches.size()); // Down + Move + Up
//
//        RRWebTouchMoveData moveData = (RRWebTouchMoveData) touches.get(1).getData();
//        Assert.assertEquals(100, moveData.getPositions().size());
//    }
//
//    @Test
//    public void testProcessTouchData_RapidTimestamps() {
//        RecordedTouchData startTouch = new RecordedTouchData(0, 100, 50.0f, 75.0f, 1000L);
//        TouchTracker tracker = new TouchTracker(startTouch);
//
//        RecordedTouchData move1 = new RecordedTouchData(2, 100, 51.0f, 76.0f, 1001L);
//        RecordedTouchData move2 = new RecordedTouchData(2, 100, 52.0f, 77.0f, 1002L);
//
//        tracker.addMoveTouch(move1);
//        tracker.addMoveTouch(move2);
//
//        RecordedTouchData endTouch = new RecordedTouchData(1, 100, 53.0f, 78.0f, 1003L);
//        tracker.addEndTouch(endTouch);
//
//        ArrayList<RRWebTouch> touches = tracker.processTouchData();
//
//        Assert.assertNotNull(touches);
//        Assert.assertEquals(3, touches.size());
//    }
//}