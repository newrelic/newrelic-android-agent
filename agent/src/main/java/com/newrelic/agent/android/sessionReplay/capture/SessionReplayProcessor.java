package com.newrelic.agent.android.sessionReplay.capture;

import com.newrelic.agent.android.sessionReplay.internal.NewRelicIdGenerator;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayViewThingyInterface;
import com.newrelic.agent.android.sessionReplay.models.Data;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.InputCapable;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebIncrementalEvent;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebInputData;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.InitialOffset;
import com.newrelic.agent.android.sessionReplay.models.Node;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.sessionReplay.models.RRWebFullSnapshotEvent;
import com.newrelic.agent.android.sessionReplay.models.RRWebMetaEvent;
import com.newrelic.agent.android.sessionReplay.models.RRWebNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionReplayProcessor {
    public static int RRWEB_TYPE_FULL_SNAPSHOT = 2;
    public static int RRWEB_TYPE_INCREMENTAL_SNAPSHOT = 3;

    private SessionReplayFrame lastFrame;
    // Track the scrim div ID so we can remove it incrementally when the dialog closes
    private int lastScrimId = -1;
    // Track the body node ID from the last full snapshot so incremental adds
    // for window roots (parentId==0) can be reparented to the body
    private int lastBodyNodeId = -1;

    public List<RRWebEvent> processFrames(List<SessionReplayFrame> rawFrames, boolean takeFullSnapshot) {
        ArrayList<RRWebEvent> snapshot = new ArrayList<>();

        for (SessionReplayFrame rawFrame : rawFrames) {
            // Incremental diff works when the base activity (rootThingy) is the
            // same screen. Window count can differ — dialog open/close is
            // handled as add/remove mutations by the diff algorithm.
            // Full snapshot is needed for: explicit request, first frame,
            // different base activity, or viewport resize.
            boolean canDiffIncrementally = lastFrame != null
                    && !takeFullSnapshot
                    && rawFrame.rootThingy != null
                    && lastFrame.rootThingy != null
                    && rawFrame.rootThingy.getViewId() == lastFrame.rootThingy.getViewId()
                    && rawFrame.width == lastFrame.width
                    && rawFrame.height == lastFrame.height;

            if (canDiffIncrementally) {
                snapshot.addAll(processIncrementalFrame(lastFrame, rawFrame));
            } else {
                addFullFrameSnapshot(snapshot, rawFrame);
            }
            lastFrame = rawFrame;
        }

        return snapshot;
    }

    RRWebFullSnapshotEvent processFullFrame(SessionReplayFrame frame) {
        // Generate style string
        StringBuilder cssStyleBuilder = new StringBuilder();

        // Process all window roots (activity, dialogs, popups) and combine
        // them into a single body. Since all elements use position: fixed,
        // they layer correctly by z-order (activity first, dialog on top).
        ArrayList<RRWebNode> bodyChildren = new ArrayList<>();
        for (int i = 0; i < frame.windowRoots.size(); i++) {
            RRWebNode windowElement = recursivelyProcessThingy(frame.windowRoots.get(i), cssStyleBuilder);
            bodyChildren.add(windowElement);

            // Only inject a scrim when a secondary window actually has
            // FLAG_DIM_BEHIND set (dialogs). Popups, toasts, and menus
            // don't dim the background.
            if (i == 0 && frame.hasDimBehind) {
                lastScrimId = NewRelicIdGenerator.generateId();
                cssStyleBuilder.append(" #dialog-scrim-")
                        .append(lastScrimId)
                        .append(" { position: fixed; top: 0; left: 0;")
                        .append(" width: ").append(frame.width).append("px;")
                        .append(" height: ").append(frame.height).append("px;")
                        .append(" background: rgba(0, 0, 0, 0.6); }");
                Attributes scrimAttrs = new Attributes("dialog-scrim-" + lastScrimId);
                RRWebElementNode scrimNode = new RRWebElementNode(
                        scrimAttrs, RRWebElementNode.TAG_TYPE_DIV, lastScrimId, new ArrayList<>());
                bodyChildren.add(scrimNode);
            } else if (i == 0) {
                lastScrimId = -1;
            }
        }

        // Generate boilerplate nodes
        // CSS node
        RRWebTextNode cssText = new RRWebTextNode(cssStyleBuilder.toString(), true, NewRelicIdGenerator.generateId());
        RRWebElementNode cssNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_STYLE, NewRelicIdGenerator.generateId(), new ArrayList<>(Collections.singletonList(cssText)));

        // Head Node
        RRWebElementNode headNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_HEAD, NewRelicIdGenerator.generateId(), new ArrayList<>(Collections.singletonList(cssNode)));

        // Body node — contains all window roots as siblings
        int bodyId = NewRelicIdGenerator.generateId();
        lastBodyNodeId = bodyId;
        RRWebElementNode bodyNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_BODY, bodyId, bodyChildren);

        // HTML node
        RRWebElementNode htmlNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_HTML, NewRelicIdGenerator.generateId(), new ArrayList<>(Arrays.asList(headNode, bodyNode)));

        Node node = new Node(RRWebNode.RRWEB_NODE_TYPE_DOCUMENT, NewRelicIdGenerator.generateId(), Collections.singletonList(htmlNode));

        Data data = new Data(new InitialOffset(0, 0), node);

        // Create Full Snapshot Root and add the above to it
        RRWebFullSnapshotEvent fullSnapshotEvent = new RRWebFullSnapshotEvent(frame.timestamp, data);

        return fullSnapshotEvent;
    }

    private RRWebNode recursivelyProcessThingy(SessionReplayViewThingyInterface rootThingy, StringBuilder cssStyleBuilder) {
        cssStyleBuilder.append(rootThingy.generateCssDescription())
                .append(" }");

        ArrayList<RRWebNode> childNodes = new ArrayList<>();
        RRWebElementNode elementNode = rootThingy.generateRRWebNode();
        for (SessionReplayViewThingyInterface childNode : rootThingy.getSubviews()) {
            RRWebNode childElement = recursivelyProcessThingy(childNode, cssStyleBuilder);
            childNodes.add(childElement);
        }

        elementNode.childNodes.addAll(childNodes);

        return elementNode;
    }

    private List<RRWebIncrementalEvent> processIncrementalFrame(SessionReplayFrame oldFrame, SessionReplayFrame newFrame) {
        List<SessionReplayViewThingyInterface> oldThingies = flattenAllWindows(oldFrame.windowRoots);
        List<SessionReplayViewThingyInterface> newThingies = flattenAllWindows(newFrame.windowRoots);

        // Use experimental set-based diffing algorithm
        ExperimentalDiffGenerator.DiffResult diffResult = ExperimentalDiffGenerator.findAddedAndRemovedItems(oldThingies, newThingies);

        List<RRWebMutationData.AddRecord> adds = new ArrayList<>();
        List<RRWebMutationData.RemoveRecord> removes = new ArrayList<>();
        List<RRWebMutationData.TextRecord> texts = new ArrayList<>();
        List<RRWebMutationData.AttributeRecord> attributes = new ArrayList<>();
        List<RRWebInputData> inputEvents = new ArrayList<>();

        // Process added items. Window roots (parentId == 0) need to be
        // reparented to the rrweb body node since they have no Android parent.
        for (SessionReplayViewThingyInterface addedItem : diffResult.getAddedItems()) {
            int parentId = addedItem.getParentViewId();
            if (parentId == 0 && lastBodyNodeId != -1) {
                parentId = lastBodyNodeId;
            }
            adds.addAll(addedItem.generateAdditionNodes(parentId));
        }

        // Process removed items
        for (SessionReplayViewThingyInterface removedItem : diffResult.getRemovedItems()) {
            int parentId = removedItem.getParentViewId();
            if (parentId == 0 && lastBodyNodeId != -1) {
                parentId = lastBodyNodeId;
            }
            RRWebMutationData.RemoveRecord removeRecord = new RRWebMutationData.RemoveRecord(
                    parentId,
                    removedItem.getViewId()
            );
            removes.add(removeRecord);
        }

        // Process updated items - create map for O(1) lookup of old items
        Map<Integer, SessionReplayViewThingyInterface> oldThingiesMap = new HashMap<>();
        for (SessionReplayViewThingyInterface item : oldThingies) {
            oldThingiesMap.put(item.getViewId(), item);
        }

        for (SessionReplayViewThingyInterface updatedNewItem : diffResult.getUpdatedItems()) {
            // Find the corresponding old item by ID using O(1) map lookup
            SessionReplayViewThingyInterface oldItem = oldThingiesMap.get(updatedNewItem.getViewId());

            if (oldItem != null) {
                // Generate differences between old and new versions
                List<MutationRecord> records = oldItem.generateDifferences(updatedNewItem);

                for (MutationRecord record : records) {
                    if (record instanceof RRWebMutationData.TextRecord) {
                        RRWebMutationData.TextRecord textRecord = (RRWebMutationData.TextRecord) record;
                        texts.add(textRecord);
                    } else if (record instanceof RRWebMutationData.AttributeRecord) {
                        RRWebMutationData.AttributeRecord attributeRecord = (RRWebMutationData.AttributeRecord) record;
                        attributes.add(attributeRecord);
                    }
                }

                // Generate input events for input-capable elements
                if (oldItem instanceof InputCapable) {
                    RRWebInputData inputData = ((InputCapable) oldItem).generateInputData(updatedNewItem);
                    if (inputData != null) {
                        inputEvents.add(inputData);
                    }
                }
            }
        }

        // Handle scrim add/remove when hasDimBehind state changes
        boolean oldHasDim = oldFrame.hasDimBehind;
        boolean newHasDim = newFrame.hasDimBehind;
        if (!oldHasDim && newHasDim) {
            // Dialog opened with dim — add scrim as an incremental mutation
            lastScrimId = NewRelicIdGenerator.generateId();
            Attributes scrimAttrs = new Attributes("dialog-scrim-" + lastScrimId);
            scrimAttrs.getMetadata().put("style",
                    "position: fixed; top: 0; left: 0; width: " + newFrame.width
                    + "px; height: " + newFrame.height + "px; background: rgba(0, 0, 0, 0.6);");
            RRWebElementNode scrimNode = new RRWebElementNode(
                    scrimAttrs, RRWebElementNode.TAG_TYPE_DIV, lastScrimId, new ArrayList<>());
            // Insert scrim BEFORE dialog views so it renders behind the dialog
            adds.add(0, new RRWebMutationData.AddRecord(lastBodyNodeId, null, scrimNode));
        } else if (oldHasDim && !newHasDim && lastScrimId != -1) {
            // Dialog closed — remove scrim
            removes.add(new RRWebMutationData.RemoveRecord(lastBodyNodeId, lastScrimId));
            lastScrimId = -1;
        }

        RRWebMutationData incrementalUpdate = new RRWebMutationData();
        incrementalUpdate.adds = adds;
        incrementalUpdate.removes = removes;
        incrementalUpdate.texts = texts;
        incrementalUpdate.attributes = attributes;

        List<RRWebIncrementalEvent> events = new ArrayList<>();
        events.add(new RRWebIncrementalEvent(newFrame.timestamp, incrementalUpdate));

        // Add separate input events (source=5) for each input state change
        for (RRWebInputData inputData : inputEvents) {
            events.add(new RRWebIncrementalEvent(newFrame.timestamp, inputData));
        }

        return events;
    }

    /**
     * Flattens all window roots into a single list for diffing.
     * When a dialog opens, its views appear as "added" items.
     * When it closes, they appear as "removed" items.
     */
    private List<SessionReplayViewThingyInterface> flattenAllWindows(List<SessionReplayViewThingyInterface> windowRoots) {
        List<SessionReplayViewThingyInterface> thingies = new ArrayList<>();
        for (SessionReplayViewThingyInterface root : windowRoots) {
            flattenTreeInto(root, thingies);
        }
        return thingies;
    }

    private void flattenTreeInto(SessionReplayViewThingyInterface rootThingy, List<SessionReplayViewThingyInterface> result) {
        List<SessionReplayViewThingyInterface> queue = new ArrayList<>();
        queue.add(rootThingy);

        while (!queue.isEmpty()) {
            SessionReplayViewThingyInterface thingy = queue.remove(queue.size() - 1);
            result.add(thingy);
            queue.addAll(thingy.getSubviews());
        }
    }

    RRWebMetaEvent createMetaEvent(SessionReplayFrame frame) {
        return new RRWebMetaEvent(
                new RRWebMetaEvent.RRWebMetaEventData(
                        "https://newrelic.com",
                        frame.width,
                        frame.height
                ),
                frame.timestamp
        );
    }

    public void onNewScreen() {
        // Reset the last frame when a new screen is detected
        lastFrame = null;
    }

    private void addFullFrameSnapshot(ArrayList<RRWebEvent> snapshot, SessionReplayFrame rawFrame) {
        RRWebMetaEvent metaEvent = createMetaEvent(rawFrame);
        snapshot.add(metaEvent);
        snapshot.add(processFullFrame(rawFrame));
    }
}
