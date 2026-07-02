package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.annotation.SuppressLint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.WorkerThread;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration;
import com.newrelic.agent.android.sessionReplay.SessionReplayLocalConfiguration;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SessionReplayMapViewThingy handles MapView components for session replay.
 * MapViews are treated as special components that don't record their subviews
 * since they contain complex map rendering that should be handled as a single unit.
 */
public class SessionReplayMapViewThingy implements SessionReplayViewThingyInterface {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();
    private final ViewDetails viewDetails;
    private final String backgroundColor;
    private final boolean shouldRecordSubviews = false; // MapViews don't record subviews

    protected SessionReplayLocalConfiguration sessionReplayLocalConfiguration;
    protected SessionReplayConfiguration sessionReplayConfiguration;

    @WorkerThread
    public SessionReplayMapViewThingy(ViewDetails viewDetails, View mapView, AgentConfiguration agentConfiguration) {
        // Validate required parameters
        if (viewDetails == null) {
            throw new IllegalArgumentException("ViewDetails cannot be null");
        }
        if (mapView == null) {
            throw new IllegalArgumentException("MapView cannot be null");
        }
        if (agentConfiguration == null) {
            throw new IllegalArgumentException("AgentConfiguration cannot be null");
        }

        this.viewDetails = viewDetails;
        this.sessionReplayLocalConfiguration = Objects.requireNonNull(
            agentConfiguration.getSessionReplayLocalConfiguration(),
            "SessionReplayLocalConfiguration cannot be null"
        );
        this.sessionReplayConfiguration = Objects.requireNonNull(
            agentConfiguration.getSessionReplayConfiguration(),
            "SessionReplayConfiguration cannot be null"
        );
        this.backgroundColor = getBackgroundColor(mapView);
    }

    @Override
    public List<? extends SessionReplayViewThingyInterface> getSubviews() {
        // Return defensive copy to prevent external modification
        return new ArrayList<>(subviews);
    }

    @Override
    public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
        // Create defensive copy to prevent external modification of internal state
        if (subviews == null) {
            this.subviews = new ArrayList<>();
        } else {
            this.subviews = new ArrayList<>(subviews);
        }
    }

    @Override
    public ViewDetails getViewDetails() {
        return viewDetails;
    }

    @Override
    public boolean shouldRecordSubviews() {
        return shouldRecordSubviews;
    }

    @Override
    public String getCssSelector() {
        return viewDetails.getCssSelector();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String generateCssDescription() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateCssDescription());
        generateMapViewCss(cssBuilder);
        return cssBuilder.toString();
    }

    @Override
    public String generateInlineCss() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateInlineCSS());
        cssBuilder.append(" ");
        generateMapViewCss(cssBuilder);
        return cssBuilder.toString();
    }

    /**
     * Generates CSS specific to MapView components
     */
    private void generateMapViewCss(StringBuilder cssBuilder) {
        cssBuilder.append("background-color: ");
        cssBuilder.append(this.backgroundColor);
        cssBuilder.append("; ");

        // Add map-specific styling
        cssBuilder.append("overflow: hidden; ");
        cssBuilder.append("position: relative; ");

        // Add a subtle pattern or placeholder for the map
        cssBuilder.append("background-image: ");
        cssBuilder.append("linear-gradient(45deg, #f0f0f0 25%, transparent 25%), ");
        cssBuilder.append("linear-gradient(-45deg, #f0f0f0 25%, transparent 25%), ");
        cssBuilder.append("linear-gradient(45deg, transparent 75%, #f0f0f0 75%), ");
        cssBuilder.append("linear-gradient(-45deg, transparent 75%, #f0f0f0 75%); ");
        cssBuilder.append("background-size: 20px 20px; ");
        cssBuilder.append("background-position: 0 0, 0 10px, 10px -10px, -10px 0px; ");
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());

        // Set the component type to identify this as a map view
        attributes.dataNrType = "mapview";

        // Add map-specific metadata
        attributes.metadata.put("data-nr-component-type", "map");

        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(), new ArrayList<>());
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplayMapViewThingy)) {
            return Collections.emptyList();
        }

        // Safe type checking for ViewDetails
        Object otherViewDetailsObj = other.getViewDetails();
        if (!(otherViewDetailsObj instanceof ViewDetails)) {
            return Collections.emptyList();
        }

        Map<String, String> styleDifferences = new HashMap<>();
        ViewDetails otherDetails = (ViewDetails) otherViewDetailsObj;

        // Check for frame changes (position/size)
        if (!Objects.equals(viewDetails.frame, otherDetails.frame)) {
            // Only add style differences if the other frame is valid
            if (otherDetails.frame != null) {
                styleDifferences.put("left", otherDetails.frame.left + "px");
                styleDifferences.put("top", otherDetails.frame.top + "px");
                styleDifferences.put("width", otherDetails.frame.width() + "px");
                styleDifferences.put("height", otherDetails.frame.height() + "px");
            } else {
                // Handle case where new frame is null (MapView was removed/hidden)
                // For MapViews, null frame indicates the view should be hidden
                styleDifferences.put("display", "none");
            }
        }

        // Check for background color changes
        if (!Objects.equals(viewDetails.backgroundColor, otherDetails.backgroundColor)) {
            styleDifferences.put("background-color", otherDetails.backgroundColor != null ? otherDetails.backgroundColor : "transparent");
        }

        // Check for visibility changes
        if (viewDetails.isHidden != otherDetails.isHidden) {
            styleDifferences.put("display", otherDetails.isHidden ? "none" : "block");
        }

        if (styleDifferences.isEmpty()) {
            return Collections.emptyList();
        }

        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.setMetadata(styleDifferences);
        attributes.dataNrType = "mapview";
        attributes.metadata.put("data-nr-component-type", "map");

        List<MutationRecord> mutations = new ArrayList<>();
        mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.getViewId(), attributes));
        return mutations;
    }

    @Override
    public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
        RRWebElementNode node = generateRRWebNode();
        node.attributes.metadata.put("style", generateInlineCss());

        RRWebMutationData.AddRecord addRecord = new RRWebMutationData.AddRecord(
                parentId,
                null,
                node);

        List<RRWebMutationData.AddRecord> adds = new ArrayList<>();
        adds.add(addRecord);
        return adds;
    }

    @Override
    public int getViewId() {
        return viewDetails.viewId;
    }

    @Override
    public int getParentViewId() {
        return viewDetails.parentId;
    }

    /**
     * Extracts background color from the map view
     */
    private String getBackgroundColor(View view) {
        Drawable background = view.getBackground();
        if (background instanceof ColorDrawable) {
            int color = ((ColorDrawable) background).getColor();
            // Ensure we only get RGB components (strip alpha) and handle negative values
            int rgbColor = color & 0xFFFFFF;
            return String.format("#%06X", rgbColor);
        }
        // Default to a light gray background for maps
        return "#E5E5E5";
    }

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        // Quick check: if it's not the same type, it has changed
        if (other == null || !(other instanceof SessionReplayMapViewThingy)) {
            return true;
        }

        SessionReplayMapViewThingy otherMapView = (SessionReplayMapViewThingy) other;

        // Compare view details
        if (!this.viewDetails.equals(otherMapView.viewDetails)) {
            return true;
        }

        // Compare background color
        if (!Objects.equals(this.backgroundColor, otherMapView.backgroundColor)) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = viewDetails != null ? viewDetails.hashCode() : 0;
        result = 31 * result + (backgroundColor != null ? backgroundColor.hashCode() : 0);
        result = 31 * result + (shouldRecordSubviews ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        SessionReplayMapViewThingy that = (SessionReplayMapViewThingy) obj;

        if (shouldRecordSubviews != that.shouldRecordSubviews) return false;
        if (viewDetails != null ? !viewDetails.equals(that.viewDetails) : that.viewDetails != null) return false;
        return backgroundColor != null ? backgroundColor.equals(that.backgroundColor) : that.backgroundColor == null;
    }
}