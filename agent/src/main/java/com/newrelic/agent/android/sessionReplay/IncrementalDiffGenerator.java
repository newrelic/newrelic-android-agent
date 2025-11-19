/*
 * Copyright (c) 2025 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This generates a diff of the thingy tree based on Heckel's Algorithm
 */
public class IncrementalDiffGenerator {

    static class Symbol {
        boolean inNew;
        Integer indexInOld;
        int occurrencesInOld = 0;
        int occurrencesInNew = 0;

        Symbol(boolean inNew, Integer indexInOld) {
            this.inNew = inNew;
            this.indexInOld = indexInOld;
        }

        Symbol(boolean inNew) {
            this(inNew, null);
        }
    }

    static class Entry {
        Symbol symbol;
        Integer index;
        boolean isSymbol;

        static Entry symbol(Symbol symbol) {
            Entry entry = new Entry();
            entry.symbol = symbol;
            entry.isSymbol = true;
            return entry;
        }

        static Entry index(int index) {
            Entry entry = new Entry();
            entry.index = index;
            entry.isSymbol = false;
            return entry;
        }
    }

    public interface Diffable {
        int getId();
        boolean hasChanged(Diffable other);
    }

    public static class Operation {
        public enum Type {
            ADD, REMOVE, UPDATE
        }

        private Type type;
        private AddChange addChange;
        private RemoveChange removeChange;
        private UpdateChange updateChange;

        private Operation(Type type) {
            this.type = type;
        }

        public static Operation add(AddChange change) {
            Operation op = new Operation(Type.ADD);
            op.addChange = change;
            return op;
        }

        public static Operation remove(RemoveChange change) {
            Operation op = new Operation(Type.REMOVE);
            op.removeChange = change;
            return op;
        }

        public static Operation update(UpdateChange change) {
            Operation op = new Operation(Type.UPDATE);
            op.updateChange = change;
            return op;
        }

        public Type getType() {
            return type;
        }

        public AddChange getAddChange() {
            return addChange;
        }

        public RemoveChange getRemoveChange() {
            return removeChange;
        }

        public UpdateChange getUpdateChange() {
            return updateChange;
        }

        public static class RemoveChange {
            private int parentId;
            private int id;

            public RemoveChange(int parentId, int id) {
                this.parentId = parentId;
                this.id = id;
            }

            public int getParentId() {
                return parentId;
            }

            public int getId() {
                return id;
            }
        }

        public static class UpdateChange {
            private SessionReplayViewThingyInterface oldElement;
            private SessionReplayViewThingyInterface newElement;

            public UpdateChange(SessionReplayViewThingyInterface oldElement, SessionReplayViewThingyInterface newElement) {
                this.oldElement = oldElement;
                this.newElement = newElement;
            }

            public SessionReplayViewThingyInterface getOldElement() {
                return oldElement;
            }

            public SessionReplayViewThingyInterface getNewElement() {
                return newElement;
            }
        }

        public static class AddChange {
            private int parentId;
            private Integer id;
            private SessionReplayViewThingyInterface node;

            public AddChange(int parentId, Integer id, SessionReplayViewThingyInterface node) {
                this.parentId = parentId;
                this.id = id;
                this.node = node;
            }

            public int getParentId() {
                return parentId;
            }

            public Integer getId() {
                return id;
            }

            public SessionReplayViewThingyInterface getNode() {
                return node;
            }
        }
    }

    public static List<Operation> generateDiff(List<SessionReplayViewThingyInterface> old, List<SessionReplayViewThingyInterface> newList) {
        Map<Integer, Symbol> table = new HashMap<>();

        // Go through the arrays according to Heckel's Algorithm.
        List<Entry> newArrayEntries = new ArrayList<>();
        List<Entry> oldArrayEntries = new ArrayList<>();

        // Pass One: Each element of the New array is gone through, and an entry in the table made for each
        for (SessionReplayViewThingyInterface item : newList) {
            Symbol entry = table.get(item.getViewId());
            if (entry == null) {
                entry = new Symbol(true);
                table.put(item.getViewId(), entry);
            }
            entry.occurrencesInNew++;
            newArrayEntries.add(Entry.symbol(entry));
        }

        // Pass Two: Each element of the Old array is gone through, and an entry in the table made for each
        for (int index = 0; index < old.size(); index++) {
            SessionReplayViewThingyInterface item = old.get(index);
            Symbol entry;
            if (!table.containsKey(item.getViewId())) {
                entry = new Symbol(false, index);
                table.put(item.getViewId(), entry);
            } else {
                entry = table.get(item.getViewId());
                // Only set indexInOld if this is the first occurrence
                if (entry.occurrencesInOld == 0) {
                    entry.indexInOld = index;
                }
            }
            entry.occurrencesInOld++;

            oldArrayEntries.add(Entry.symbol(entry));
        }

        // Pass Three: Use the first observation of the algorithm's paper. If any entry occurs only once
        // in the list, then it must be the same entry, although it could have been moved. Cross reference
        // the two
        for (int index = 0; index < newArrayEntries.size(); index++) {
            Entry item = newArrayEntries.get(index);
            // Only match if element appears exactly once in both old AND new
            if (item.isSymbol && item.symbol.inNew && item.symbol.indexInOld != null
                    && item.symbol.occurrencesInOld == 1 && item.symbol.occurrencesInNew == 1) {
                newArrayEntries.set(index, Entry.index(item.symbol.indexInOld));
                oldArrayEntries.set(item.symbol.indexInOld, Entry.index(index));
            }
        }

        // Pass Four: Use the second observation of the algorithm's paper. If NewArray[i] points to OldArray[j],
        // and NewArray[i+1] and OldArray[j+1] contain identical symbol table entries, then OldArray[j+1] is
        // set to line i+1 and NewArray[i+1] is set to line j+1
        if (newArrayEntries.size() > 1) {
            for (int i = 0; i < newArrayEntries.size() - 1; i++) {
                Entry entry = newArrayEntries.get(i);
                if (!entry.isSymbol) {
                    int j = entry.index;
                    if (j + 1 < oldArrayEntries.size()) {
                        Entry newEntry = newArrayEntries.get(i + 1);
                        Entry oldEntry = oldArrayEntries.get(j + 1);
                        if (newEntry.isSymbol && oldEntry.isSymbol && newEntry.symbol == oldEntry.symbol) {
                            newArrayEntries.set(i + 1, Entry.index(j + 1));
                            oldArrayEntries.set(j + 1, Entry.index(i + 1));
                        }
                    }
                }
            }
        }

        // Pass Five: Same as pass 4, but in reverse!
        if (newArrayEntries.size() > 1) {
            for (int i = newArrayEntries.size() - 1; i > 0; i--) {
                Entry entry = newArrayEntries.get(i);
                if (!entry.isSymbol) {
                    int j = entry.index;
                    if (j - 1 >= 0) {
                        Entry newEntry = newArrayEntries.get(i - 1);
                        Entry oldEntry = oldArrayEntries.get(j - 1);
                        if (newEntry.isSymbol && oldEntry.isSymbol && newEntry.symbol == oldEntry.symbol) {
                            newArrayEntries.set(i - 1, Entry.index(j - 1));
                            oldArrayEntries.set(j - 1, Entry.index(i - 1));
                        }
                    }
                }
            }
        }

        // Let's get those changes
        List<Operation> changes = new ArrayList<>();

        // Removals
        int[] deleteOffsets = new int[oldArrayEntries.size()];
        int runningOffset = 0;
        for (int index = 0; index < oldArrayEntries.size(); index++) {
            deleteOffsets[index] = runningOffset;
            Entry entry = oldArrayEntries.get(index);
            if (entry.isSymbol) {
                changes.add(Operation.remove(new Operation.RemoveChange(old.get(index).getParentViewId(), old.get(index).getViewId())));
                runningOffset++;
            }
        }

        runningOffset = 0;

        // Additions and Alterations
        for (int index = 0; index < newArrayEntries.size(); index++) {
            Entry entry = newArrayEntries.get(index);
            if (entry.isSymbol) {
                changes.add(Operation.add(new Operation.AddChange(newList.get(index).getParentViewId(), newList.get(index).getViewId(), newList.get(index))));
                runningOffset++;
            } else {
                int indexInOld = entry.index;
                int deleteOffset = deleteOffsets[indexInOld];
                SessionReplayViewThingyInterface newElement = newList.get(index);
                SessionReplayViewThingyInterface oldElement = old.get(indexInOld);

                if ((indexInOld - deleteOffset + runningOffset) != index) {
                    // If this doesn't get us back to where we currently are, then
                    // the thing was moved
                    changes.add(Operation.remove(new Operation.RemoveChange(newElement.getParentViewId(), newElement.getViewId())));
                    changes.add(Operation.add(new Operation.AddChange(newElement.getParentViewId(), newElement.getViewId(), newElement)));
                } else if (newElement.getClass() == oldElement.getClass()) {
                    // Use the hasChanged method from Diffable interface instead of hashCode
                    if (newElement.hasChanged(oldElement)) {
                        changes.add(Operation.update(new Operation.UpdateChange(oldElement, newElement)));
                    }
                }
            }
        }

        return changes;
    }
}