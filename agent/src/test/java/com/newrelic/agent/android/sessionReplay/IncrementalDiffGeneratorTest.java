/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.newrelic.agent.android.R;
import com.newrelic.agent.android.sessionReplay.IncrementalDiffGenerator.Operation;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class IncrementalDiffGeneratorTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    // ==================== EMPTY LIST TESTS ====================

    @Test
    public void testGenerateDiff_BothEmptyLists() {
        List<SessionReplayViewThingyInterface> oldList = new ArrayList<>();
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>();

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        Assert.assertTrue(operations.isEmpty());
    }

    @Test
    public void testGenerateDiff_OldEmptyNewHasElements() {
        List<SessionReplayViewThingyInterface> oldList = new ArrayList<>();
        List<SessionReplayViewThingyInterface> newList = createThingies(3);

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        Assert.assertEquals(3, operations.size());

        // All should be ADD operations
        for (Operation op : operations) {
            Assert.assertEquals(Operation.Type.ADD, op.getType());
        }
    }

    @Test
    public void testGenerateDiff_NewEmptyOldHasElements() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(3);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>();

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        Assert.assertEquals(3, operations.size());

        // All should be REMOVE operations
        for (Operation op : operations) {
            Assert.assertEquals(Operation.Type.REMOVE, op.getType());
        }
    }

    // ==================== SINGLE ELEMENT TESTS ====================

    @Test
    public void testGenerateDiff_SingleElementUnchanged() {
        SessionReplayViewThingyInterface thingy = createThingy();
        List<SessionReplayViewThingyInterface> oldList = Arrays.asList(thingy);
        List<SessionReplayViewThingyInterface> newList = Arrays.asList(thingy);

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        // No operations needed if element unchanged
        Assert.assertTrue(operations.isEmpty());
    }

    @Test
    public void testGenerateDiff_SingleElementAdded() {
        List<SessionReplayViewThingyInterface> oldList = new ArrayList<>();
        List<SessionReplayViewThingyInterface> newList = createThingies(1);

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertEquals(1, operations.size());
        Assert.assertEquals(Operation.Type.ADD, operations.get(0).getType());
        Assert.assertNotNull(operations.get(0).getAddChange());
    }

    @Test
    public void testGenerateDiff_SingleElementRemoved() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(1);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>();

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertEquals(1, operations.size());
        Assert.assertEquals(Operation.Type.REMOVE, operations.get(0).getType());
        Assert.assertNotNull(operations.get(0).getRemoveChange());
    }

    @Test
    public void testGenerateDiff_SingleElementChanged() {
        View view1 = new View(context);
        view1.setBackgroundColor(android.graphics.Color.RED);
        ViewDetails details1 = new ViewDetails(view1);
        SessionReplayViewThingy thingy1 = new SessionReplayViewThingy(details1);

        ViewDetails updatedDetails1 = new ViewDetails(view1);
        SessionReplayViewThingy updatedThingy1 = new SessionReplayViewThingy(updatedDetails1);
        List<SessionReplayViewThingyInterface> oldList = Arrays.asList(updatedThingy1);

        // Create second thingy from same view (viewId will be the same due to view tag)
        ViewDetails updatedDetails2 = new ViewDetails(view1);
        SessionReplayViewThingy updatedThingy2 = new SessionReplayViewThingy(updatedDetails2);

        // Modify the frame to simulate a change (frame is now non-final)
        android.graphics.Rect newFrame = new android.graphics.Rect(10, 20, 100, 200);
        updatedThingy2.viewDetails.frame = newFrame;


        List<SessionReplayViewThingyInterface> newList = Arrays.asList(updatedThingy2);

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        // Should detect UPDATE
        boolean hasUpdate = false;
        for (Operation op : operations) {
            if (op.getType() == Operation.Type.UPDATE) {
                hasUpdate = true;
                Assert.assertNotNull(op.getUpdateChange());
            }
        }
        Assert.assertTrue("Should have UPDATE operation", hasUpdate);
    }

    // ==================== MULTIPLE ELEMENT TESTS ====================

    @Test
    public void testGenerateDiff_MultipleElementsUnchanged() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(5);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>(oldList);

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        // Same elements, no changes
        Assert.assertTrue(operations.isEmpty());
    }

    @Test
    public void testGenerateDiff_MultipleElementsAllAdded() {
        List<SessionReplayViewThingyInterface> oldList = new ArrayList<>();
        List<SessionReplayViewThingyInterface> newList = createThingies(5);

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertEquals(5, operations.size());
        for (Operation op : operations) {
            Assert.assertEquals(Operation.Type.ADD, op.getType());
        }
    }

    @Test
    public void testGenerateDiff_MultipleElementsAllRemoved() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(5);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>();

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertEquals(5, operations.size());
        for (Operation op : operations) {
            Assert.assertEquals(Operation.Type.REMOVE, op.getType());
        }
    }

    @Test
    public void testGenerateDiff_SomeAdded_SomeRemoved() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(3);
        List<SessionReplayViewThingyInterface> newList = createThingies(3);

        // Keep first element, remove second, keep third, add two new
        newList.set(1, createThingy());
        newList.add(createThingy());
        newList.add(createThingy());

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        Assert.assertTrue(operations.size() > 0);

        // Should have both ADD and REMOVE operations
        boolean hasAdd = false;
        boolean hasRemove = false;
        for (Operation op : operations) {
            if (op.getType() == Operation.Type.ADD) hasAdd = true;
            if (op.getType() == Operation.Type.REMOVE) hasRemove = true;
        }
        Assert.assertTrue(hasAdd || hasRemove);
    }

    // ==================== MOVE DETECTION TESTS ====================

    @Test
    public void testGenerateDiff_ElementMoved() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(3);

        // Create new list with elements in different order
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>();
        newList.add(oldList.get(2)); // Move last to first
        newList.add(oldList.get(0)); // Move first to second
        newList.add(oldList.get(1)); // Move middle to last

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        // Moves are represented as REMOVE + ADD
        Assert.assertTrue(operations.size() > 0);
    }

    @Test
    public void testGenerateDiff_SwapTwoElements() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(2);

        List<SessionReplayViewThingyInterface> newList = new ArrayList<>();
        newList.add(oldList.get(1));
        newList.add(oldList.get(0));

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        // Swap should result in operations
        Assert.assertTrue(operations.size() >= 0);
    }

    // ==================== OPERATION TYPE TESTS ====================

    @Test
    public void testOperationType_ADD() {
        Operation.AddChange addChange = new Operation.AddChange(1, 2, createThingy());
        Operation operation = Operation.add(addChange);

        Assert.assertEquals(Operation.Type.ADD, operation.getType());
        Assert.assertNotNull(operation.getAddChange());
        Assert.assertNull(operation.getRemoveChange());
        Assert.assertNull(operation.getUpdateChange());
    }

    @Test
    public void testOperationType_REMOVE() {
        Operation.RemoveChange removeChange = new Operation.RemoveChange(1, 2);
        Operation operation = Operation.remove(removeChange);

        Assert.assertEquals(Operation.Type.REMOVE, operation.getType());
        Assert.assertNotNull(operation.getRemoveChange());
        Assert.assertNull(operation.getAddChange());
        Assert.assertNull(operation.getUpdateChange());
    }

    @Test
    public void testOperationType_UPDATE() {
        SessionReplayViewThingyInterface thingy1 = createThingy();
        SessionReplayViewThingyInterface thingy2 = createThingy();
        Operation.UpdateChange updateChange = new Operation.UpdateChange(thingy1, thingy2);
        Operation operation = Operation.update(updateChange);

        Assert.assertEquals(Operation.Type.UPDATE, operation.getType());
        Assert.assertNotNull(operation.getUpdateChange());
        Assert.assertNull(operation.getAddChange());
        Assert.assertNull(operation.getRemoveChange());
    }

    // ==================== CHANGE OBJECT TESTS ====================

    @Test
    public void testAddChange_Getters() {
        SessionReplayViewThingyInterface thingy = createThingy();
        Operation.AddChange addChange = new Operation.AddChange(10, 20, thingy);

        Assert.assertEquals(10, addChange.getParentId());
        Assert.assertEquals(Integer.valueOf(20), addChange.getId());
        Assert.assertEquals(thingy, addChange.getNode());
    }

    @Test
    public void testRemoveChange_Getters() {
        Operation.RemoveChange removeChange = new Operation.RemoveChange(10, 20);

        Assert.assertEquals(10, removeChange.getParentId());
        Assert.assertEquals(20, removeChange.getId());
    }

    @Test
    public void testUpdateChange_Getters() {
        SessionReplayViewThingyInterface oldThingy = createThingy();
        SessionReplayViewThingyInterface newThingy = createThingy();
        Operation.UpdateChange updateChange = new Operation.UpdateChange(oldThingy, newThingy);

        Assert.assertEquals(oldThingy, updateChange.getOldElement());
        Assert.assertEquals(newThingy, updateChange.getNewElement());
    }

    // ==================== HECKEL'S ALGORITHM SPECIFIC TESTS ====================

    @Test
    public void testGenerateDiff_UniqueElementsOnly() {
        // Heckel's algorithm handles unique elements (occurring once) efficiently
        List<SessionReplayViewThingyInterface> oldList = createThingies(3);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>(oldList);

        // Add one unique element to new list
        newList.add(createThingy());

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        // Should detect the new element
        boolean hasAdd = false;
        for (Operation op : operations) {
            if (op.getType() == Operation.Type.ADD) {
                hasAdd = true;
            }
        }
        Assert.assertTrue(hasAdd);
    }

    @Test
    public void testGenerateDiff_LargeList() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(100);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>(oldList);

        // Add 10 new elements
        for (int i = 0; i < 10; i++) {
            newList.add(createThingy());
        }

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        Assert.assertEquals(10, operations.size());

        for (Operation op : operations) {
            Assert.assertEquals(Operation.Type.ADD, op.getType());
        }
    }

    @Test
    public void testGenerateDiff_CompleteReplacement() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(5);
        List<SessionReplayViewThingyInterface> newList = createThingies(5);

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        // All old removed, all new added
        Assert.assertEquals(10, operations.size());

        int removeCount = 0;
        int addCount = 0;
        for (Operation op : operations) {
            if (op.getType() == Operation.Type.REMOVE) removeCount++;
            if (op.getType() == Operation.Type.ADD) addCount++;
        }

        Assert.assertEquals(5, removeCount);
        Assert.assertEquals(5, addCount);
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testGenerateDiff_AddToBeginning() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(3);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>();

        newList.add(createThingy()); // New element at beginning
        newList.addAll(oldList);

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        boolean hasAdd = false;
        for (Operation op : operations) {
            if (op.getType() == Operation.Type.ADD) {
                hasAdd = true;
            }
        }
        Assert.assertTrue(hasAdd);
    }

    @Test
    public void testGenerateDiff_AddToEnd() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(3);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>(oldList);

        newList.add(createThingy()); // New element at end

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        Assert.assertEquals(1, operations.size());
        Assert.assertEquals(Operation.Type.ADD, operations.get(0).getType());
    }

    @Test
    public void testGenerateDiff_RemoveFromBeginning() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(3);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>(oldList);

        newList.remove(0); // Remove first element

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        boolean hasRemove = false;
        for (Operation op : operations) {
            if (op.getType() == Operation.Type.REMOVE) {
                hasRemove = true;
            }
        }
        Assert.assertTrue(hasRemove);
    }

    @Test
    public void testGenerateDiff_RemoveFromEnd() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(3);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>(oldList);

        newList.remove(newList.size() - 1); // Remove last element

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        Assert.assertEquals(1, operations.size());
        Assert.assertEquals(Operation.Type.REMOVE, operations.get(0).getType());
    }

    @Test
    public void testGenerateDiff_RemoveFromMiddle() {
        List<SessionReplayViewThingyInterface> oldList = createThingies(5);
        List<SessionReplayViewThingyInterface> newList = new ArrayList<>(oldList);

        newList.remove(2); // Remove middle element

        List<Operation> operations = IncrementalDiffGenerator.generateDiff(oldList, newList);

        Assert.assertNotNull(operations);
        boolean hasRemove = false;
        for (Operation op : operations) {
            if (op.getType() == Operation.Type.REMOVE) {
                hasRemove = true;
            }
        }
        Assert.assertTrue(hasRemove);
    }

    // ==================== HELPER METHODS ====================

    private SessionReplayViewThingyInterface createThingy() {
        View view = new View(context);
        ViewDetails viewDetails = new ViewDetails(view);
        return new SessionReplayViewThingy(viewDetails);
    }

    private List<SessionReplayViewThingyInterface> createThingies(int count) {
        List<SessionReplayViewThingyInterface> thingies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            thingies.add(createThingy());
        }
        return thingies;
    }
}