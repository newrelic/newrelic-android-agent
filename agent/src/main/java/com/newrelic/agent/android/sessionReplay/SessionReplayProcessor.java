package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.Data;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebIncrementalEvent;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.InitialOffset;
import com.newrelic.agent.android.sessionReplay.models.Node;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.sessionReplay.models.RRWebFullSnapshotEvent;
import com.newrelic.agent.android.sessionReplay.models.RRWebNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SessionReplayProcessor {
    public static int RRWEB_TYPE_FULL_SNAPSHOT = 2;
    public static int RRWEB_TYPE_INCREMENTAL_SNAPSHOT = 3;

    private SessionReplayFrame lastFrame;

    public List<RRWebEvent> processFrames(List<SessionReplayFrame> rawFrames) {
        ArrayList<RRWebEvent> snapshot = new ArrayList<>();

        for(SessionReplayFrame rawFrame : rawFrames) {
            // We need to come up with a way to tell if the activity or fragment is different.
            if(lastFrame == null)  {
                snapshot.add(processFullFrame(rawFrame));
            } else {
                if(rawFrame.rootThingy.getViewId() == lastFrame.rootThingy.getViewId()) {
                    snapshot.add(processIncrementalFrame(lastFrame, rawFrame));
                } else {
                    snapshot.add(processFullFrame(rawFrame));
                }
            }
            lastFrame = rawFrame;
        }

        return snapshot;
    }

    private RRWebFullSnapshotEvent processFullFrame(SessionReplayFrame frame) {
        // Generate style string
        StringBuilder cssStyleBuilder = new StringBuilder();

        // Generate Body node
        RRWebNode rootElement = recursivelyProcessThingy(frame.rootThingy, cssStyleBuilder);

        // Generate boilerplate nodes
        // CSS node
        RRWebTextNode cssText = new RRWebTextNode(cssStyleBuilder.toString(), true, NewRelicIdGenerator.generateId());
        RRWebElementNode cssNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_STYLE, NewRelicIdGenerator.generateId(), Collections.singletonList(cssText));

        // Head Node
        RRWebElementNode headNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_HEAD, NewRelicIdGenerator.generateId(), Collections.singletonList(cssNode));

        // Body node
        RRWebElementNode bodyNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_BODY, NewRelicIdGenerator.generateId(), Collections.singletonList(rootElement));

        // HTML node
        RRWebElementNode htmlNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_HTML,NewRelicIdGenerator.generateId(), Arrays.asList(headNode, bodyNode));

        Node node = new Node(RRWebNode.RRWEB_NODE_TYPE_DOCUMENT, NewRelicIdGenerator.generateId(), Collections.singletonList(htmlNode));

        Data data = new Data(new InitialOffset(0, 0), node);

        // Create Full Snapshot Root and add the above to it
        RRWebFullSnapshotEvent fullSnapshotEvent = new RRWebFullSnapshotEvent(frame.timestamp, data);

        return fullSnapshotEvent;
    }

    private RRWebNode recursivelyProcessThingy(SessionReplayViewThingyInterface rootThingy, StringBuilder cssStyleBuilder) {
        cssStyleBuilder.append(rootThingy.generateCssDescription())
                .append(" }");

//        Attributes attribues = new Attributes(rootThingy.getCSSSelector());
        ArrayList<RRWebNode> childNodes = new ArrayList<>();
        RRWebElementNode elementNode = rootThingy.generateRRWebNode();
        for(SessionReplayViewThingyInterface childNode : rootThingy.getSubviews()) {
            RRWebNode childElement = recursivelyProcessThingy(childNode, cssStyleBuilder);
            childNodes.add(childElement);
        }

//        elementNode.childNodes.addAll(childNodes);
        elementNode.childNodes.addAll(childNodes);

        return elementNode;
    }

    private RRWebIncrementalEvent processIncrementalFrame(SessionReplayFrame oldFrame, SessionReplayFrame newFrame) {
        List<SessionReplayViewThingyInterface> oldThingies = flattenTree(oldFrame.rootThingy);
        List<SessionReplayViewThingyInterface> newThingies = flattenTree(newFrame.rootThingy);

        List<IncrementalDiffGenerator.Operation> operations = IncrementalDiffGenerator.generateDiff(oldThingies, newThingies);

        List<RRWebMutationData.AddRecord> adds = new ArrayList<>();
        List<RRWebMutationData.RemoveRecord> removes = new ArrayList<>();
        List<RRWebMutationData.TextRecord> texts = new ArrayList<>();
        List<RRWebMutationData.AttributeRecord> attributes = new ArrayList<>();

        for (IncrementalDiffGenerator.Operation operation : operations) {
            switch (operation.getType()) {
                case ADD:
                    RRWebElementNode node = operation.getAddChange().getNode().generateRRWebNode();
                    node.attributes.metadata.put("style", operation.getAddChange().getNode().generateCssDescription());
                    RRWebMutationData.AddRecord addRecord = new RRWebMutationData.AddRecord(
                            operation.getAddChange().getParentId(),
                            operation.getAddChange().getId()+1,
                            node
                    );
                    adds.add(addRecord);
                    break;
                case REMOVE:
                    RRWebMutationData.RemoveRecord removeRecord = new RRWebMutationData.RemoveRecord(
                            operation.getRemoveChange().getParentId(),
                            operation.getRemoveChange().getId()
                    );
                    removes.add(removeRecord);
                    break;
                case UPDATE:
                    // For updates, we need to handle the mutations
                    List<MutationRecord> records = operation.getUpdateChange().getOldElement().generateDifferences(
                            operation.getUpdateChange().getNewElement()
                    );

                    for (MutationRecord record : records) {
                        if(record instanceof RRWebMutationData.TextRecord) {
                            RRWebMutationData.TextRecord textRecord = (RRWebMutationData.TextRecord) record;
                            texts.add(textRecord);
                        } else if(record instanceof RRWebMutationData.AttributeRecord) {
                            RRWebMutationData.AttributeRecord attributeRecord = (RRWebMutationData.AttributeRecord) record;
                            attributes.add(attributeRecord);
                        } else {
                            continue;
                        }
                    }
                    break;
            }
        }

        RRWebMutationData incrementalUpdate = new RRWebMutationData();
        incrementalUpdate.adds = adds;
        incrementalUpdate.removes = removes;
        incrementalUpdate.texts = texts;
        incrementalUpdate.attributes = attributes;

        // Return the incremental event
        return new RRWebIncrementalEvent(newFrame.timestamp, incrementalUpdate);
    }

    private List<SessionReplayViewThingyInterface> flattenTree(SessionReplayViewThingyInterface rootThingy) {
        List<SessionReplayViewThingyInterface> thingies = new ArrayList<>();
        List<SessionReplayViewThingyInterface> queue = new ArrayList<>();
        queue.add(rootThingy);

        while (!queue.isEmpty()) {
            // In Swift, popLast() removes and returns the last element
            // In Java, we'll remove from the end of the list to mimic this behavior
            SessionReplayViewThingyInterface thingy = queue.remove(queue.size() - 1);
            thingies.add(thingy);

            // In Swift, append(contentsOf:) adds all elements from another collection
            // In Java, we'll add all elements from the subviews list
            queue.addAll(thingy.getSubviews());
        }

        return thingies;
    }

    public void onNewScreen() {
        // Reset the last frame when a new screen is detected
        lastFrame = null;
    }
}
