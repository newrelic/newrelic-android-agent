/*
 * Copyright (c) 2025 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

/**
 * Experimental set-based diffing algorithm as an alternative to Heckel's algorithm.
 *
 * This implementation uses HashSet operations to find added, removed, and updated items
 * between two snapshots of the view hierarchy. It's simpler than Heckel's algorithm but
 * doesn't detect move operations (moves are treated as remove + add).
 *
 * Performance: O(n) time complexity with O(n) space for the hash maps and sets.
 *
 * @see IncrementalDiffGenerator For the current Heckel-based implementation
 */
object ExperimentalDiffGenerator {

    /**
     * Result of the diff operation containing added, removed, and updated items.
     *
     * This is a data class for better Java interop than using Kotlin's Triple.
     */
    data class DiffResult(
        val addedItems: List<SessionReplayViewThingyInterface>,
        val removedItems: List<SessionReplayViewThingyInterface>,
        val updatedItems: List<SessionReplayViewThingyInterface>
    )

    /**
     * Finds added, removed, and updated items between two view hierarchy snapshots.
     *
     * Algorithm:
     * 1. Create ID-based maps for O(1) lookups
     * 2. Use set operations to find added IDs (newIds - oldIds)
     * 3. Use set operations to find removed IDs (oldIds - newIds)
     * 4. Find intersection (same IDs in both) and check for updates using hasChanged()
     *
     * @param oldItems The previous snapshot of the view hierarchy (flattened)
     * @param newItems The current snapshot of the view hierarchy (flattened)
     * @return DiffResult containing lists of added, removed, and updated items
     */
    @JvmStatic
    fun findAddedAndRemovedItems(
        oldItems: List<SessionReplayViewThingyInterface>,
        newItems: List<SessionReplayViewThingyInterface>
    ): DiffResult {
        // Create maps for O(1) lookups by view ID
        val oldMap = oldItems.associateBy { it.viewId }
        val newMap = newItems.associateBy { it.viewId }

        // Create HashSets to track unique IDs for efficient set operations
        val oldItemIds = HashSet(oldItems.map { it.viewId })
        val newItemIds = HashSet(newItems.map { it.viewId })

        // Find added items by subtracting oldItemIds from newItemIds
        val addedIds = newItemIds - oldItemIds
        val addedItems = newItems.filter { it.viewId in addedIds }

        // Find removed items by subtracting newItemIds from oldItemIds
        val removedIds = oldItemIds - newItemIds
        val removedItems = oldItems.filter { it.viewId in removedIds }

        // Find updated items by finding the intersection of oldItemIds and newItemIds
        val sameIds = oldItemIds.intersect(newItemIds)
        val updatedItems = mutableListOf<SessionReplayViewThingyInterface>()

        for (id in sameIds) {
            val oldItem = oldMap[id] ?: continue
            val newItem = newMap[id] ?: continue

            // Use the hasChanged() method from SessionReplayViewThingyInterface
            // to determine if the view has been updated
            if (oldItem.hasChanged(newItem)) {
                updatedItems.add(newItem)
            }
        }

        return DiffResult(addedItems, removedItems, updatedItems)
    }

    /**
     * Generates diff operations compatible with IncrementalDiffGenerator.Operation format.
     *
     * This method bridges the experimental set-based algorithm with the existing
     * operation-based format used by SessionReplayProcessor.
     *
     * @param oldItems The previous snapshot of the view hierarchy (flattened)
     * @param newItems The current snapshot of the view hierarchy (flattened)
     * @return List of operations (ADD, REMOVE, UPDATE) in the same format as Heckel algorithm
     */
    @JvmStatic
    fun generateDiff(
        oldItems: List<SessionReplayViewThingyInterface>,
        newItems: List<SessionReplayViewThingyInterface>
    ): List<IncrementalDiffGenerator.Operation> {
        val result = findAddedAndRemovedItems(oldItems, newItems)
        val operations = mutableListOf<IncrementalDiffGenerator.Operation>()

        // Convert added items to ADD operations
        for (item in result.addedItems) {
            val addChange = IncrementalDiffGenerator.Operation.AddChange(
                item.parentViewId,
                item.viewId,
                item
            )
            operations.add(IncrementalDiffGenerator.Operation.add(addChange))
        }

        // Convert removed items to REMOVE operations
        for (item in result.removedItems) {
            val removeChange = IncrementalDiffGenerator.Operation.RemoveChange(
                item.parentViewId,
                item.viewId
            )
            operations.add(IncrementalDiffGenerator.Operation.remove(removeChange))
        }

        // Convert updated items to UPDATE operations
        for (newItem in result.updatedItems) {
            // Find the corresponding old item
            val oldItem = oldItems.find { it.viewId == newItem.viewId }
            if (oldItem != null) {
                val updateChange = IncrementalDiffGenerator.Operation.UpdateChange(
                    oldItem,
                    newItem
                )
                operations.add(IncrementalDiffGenerator.Operation.update(updateChange))
            }
        }

        return operations
    }
}