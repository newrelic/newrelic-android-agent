package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.Data;
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
import java.util.List;

public class SessionReplayProcessor {
    public static int RRWEB_TYPE_FULL_SNAPSHOT = 2;
    public static int RRWEB_TYPE_INCREMENTAL_SNAPSHOT = 3;
    private SessionReplayFrame lastFrame;

    public List<RRWebEvent> processFrames(List<SessionReplayFrame> rawFrames) {
        ArrayList<RRWebEvent> snapshot = new ArrayList<>();

        for (SessionReplayFrame rawFrame : rawFrames) {
            // Process each frame and add all resulting events to the snapshot
            snapshot.addAll(processFrame(rawFrame));
            lastFrame = rawFrame;
        }

        return snapshot;
    }

    public List<RRWebEvent> processFrame(SessionReplayFrame frame) {
        List<RRWebEvent> events = new ArrayList<>();

        // Create meta event
        RRWebMetaEvent metaEvent = new RRWebMetaEvent(
                new RRWebMetaEvent.RRWebMetaEventData(
                        "https://newrelic.com",
                        frame.width,
                        frame.height
                ),
                frame.timestamp
        );
        events.add(metaEvent);

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
        RRWebElementNode htmlNode = new RRWebElementNode(null, RRWebElementNode.TAG_TYPE_HTML, NewRelicIdGenerator.generateId(), Arrays.asList(headNode, bodyNode));

        Node node = new Node(RRWebNode.RRWEB_NODE_TYPE_DOCUMENT, NewRelicIdGenerator.generateId(), Collections.singletonList(htmlNode));

        Data data = new Data(new InitialOffset(0, 0), node);

        // Create Full Snapshot Root and add the above to it
        RRWebFullSnapshotEvent fullSnapshotEvent = new RRWebFullSnapshotEvent(frame.timestamp, data);
        events.add(fullSnapshotEvent);

        return events;
    }

    private RRWebNode recursivelyProcessThingy(SessionReplayViewThingyInterface rootThingy, StringBuilder cssStyleBuilder) {
        cssStyleBuilder.append(rootThingy.generateCssDescription());

//        Attributes attribues = new Attributes(rootThingy.getCSSSelector());
        ArrayList<RRWebNode> childNodes = new ArrayList<>();
        RRWebElementNode elementNode = rootThingy.generateRRWebNode();
        for (SessionReplayViewThingyInterface childNode : rootThingy.getSubviews()) {
            RRWebNode childElement = recursivelyProcessThingy(childNode, cssStyleBuilder);
            childNodes.add(childElement);
        }

//        elementNode.childNodes.addAll(childNodes);
        elementNode.childNodes.addAll(childNodes);

        return elementNode;
    }
}
