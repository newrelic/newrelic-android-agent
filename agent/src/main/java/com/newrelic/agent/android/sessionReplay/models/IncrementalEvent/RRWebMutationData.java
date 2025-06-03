package com.newrelic.agent.android.sessionReplay.models.IncrementalEvent;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.List;

public class RRWebMutationData extends RRWebIncrementalData {
    public List<AddRecord> adds;
    public List<RemoveRecord> removes;
    public List<TextRecord> texts;
    public List<AttributeRecord> attributes;

    public RRWebMutationData() {
        this.source = RRWebIncrementalSource.MUTATION;
    }

    public static class AddRecord implements MutationRecord {
        public int parentId;
        public int nextId;
        public RRWebElementNode node;

        public AddRecord(int parentId, int nextId, RRWebElementNode node) {
            this.parentId = parentId;
            this.nextId = nextId;
            this.node = node;
        }
    }

    public static class RemoveRecord implements MutationRecord {
        public int parentId;
        public int id;

        public RemoveRecord(int parentId, int id) {
            this.parentId = parentId;
            this.id = id;
        }
    }

    public static class TextRecord implements MutationRecord {
        public int id;
        public String value;

        public TextRecord(int id, String value) {
            this.id = id;
            this.value = value;
        }
    }

    public static class AttributeRecord implements MutationRecord {
        public int id;
        public Attributes attributes;

        public AttributeRecord(int id, Attributes attributes) {
            this.id = id;
            this.attributes = attributes;
        }
    }
}