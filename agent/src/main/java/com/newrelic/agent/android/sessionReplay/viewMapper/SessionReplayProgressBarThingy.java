package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.widget.ProgressBar;

import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingy;
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Handles rendering of ProgressBar views in session replay.
 * - Indeterminate: renders as {@code <progress>} (browser shows animated bar)
 * - Determinate: renders as {@code <progress value="X" max="Y">} (browser shows filled bar)
 */
public class SessionReplayProgressBarThingy extends SessionReplayViewThingy {
    private final boolean isIndeterminate;
    private final int progress;
    private final int max;
    private final ViewDetails viewDetails;

    public SessionReplayProgressBarThingy(ViewDetails viewDetails, ProgressBar view) {
        super(viewDetails);
        this.viewDetails = viewDetails;
        this.isIndeterminate = view.isIndeterminate();
        this.progress = view.getProgress();
        this.max = view.getMax();
    }

    private Attributes buildAttributes() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        if (!isIndeterminate) {
            attributes.value = String.valueOf(progress);
            attributes.max = String.valueOf(max);
        }
        return attributes;
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        return new RRWebElementNode(
                buildAttributes(),
                RRWebElementNode.TAG_TYPE_PROGRESS,
                viewDetails.viewId,
                new ArrayList<>()
        );
    }

    @Override
    public String generateCssDescription() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateCssDescription());
        generateProgressCss(cssBuilder);
        return cssBuilder.toString();
    }

    @Override
    public String generateInlineCss() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateInlineCSS());
        cssBuilder.append(" ");
        generateProgressCss(cssBuilder);
        return cssBuilder.toString();
    }

    private void generateProgressCss(StringBuilder cssBuilder) {
        // Ensure the progress element is visible with a minimum height
            cssBuilder.append("accent-color: #1a73e8; ");
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplayProgressBarThingy)) {
            return Collections.emptyList();
        }

        SessionReplayProgressBarThingy otherBar = (SessionReplayProgressBarThingy) other;
        List<MutationRecord> parentDifferences = super.generateDifferences(other);
        boolean hasValueChange = this.progress != otherBar.progress
                || this.max != otherBar.max
                || this.isIndeterminate != otherBar.isIndeterminate;

        if ((parentDifferences == null || parentDifferences.isEmpty()) && !hasValueChange) {
            return Collections.emptyList();
        }

        List<MutationRecord> mutations = new ArrayList<>();
        if (parentDifferences != null) {
            mutations.addAll(parentDifferences);
        }

        if (hasValueChange) {
            mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.viewId, otherBar.buildAttributes()));
        }

        return mutations;
    }

    @Override
    public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
        RRWebElementNode viewNode = new RRWebElementNode(
                buildAttributes(),
                RRWebElementNode.TAG_TYPE_PROGRESS,
                viewDetails.viewId,
                new ArrayList<>()
        );
        viewNode.attributes.metadata.put("style", generateInlineCss());

        return Collections.singletonList(new RRWebMutationData.AddRecord(parentId, null, viewNode));
    }

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplayProgressBarThingy)) {
            return true;
        }
        return this.hashCode() != other.hashCode();
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewDetails, progress, max, isIndeterminate);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SessionReplayProgressBarThingy)) return false;

        SessionReplayProgressBarThingy that = (SessionReplayProgressBarThingy) other;
        return viewDetails.equals(that.viewDetails)
                && progress == that.progress
                && max == that.max
                && isIndeterminate == that.isIndeterminate;
    }
}