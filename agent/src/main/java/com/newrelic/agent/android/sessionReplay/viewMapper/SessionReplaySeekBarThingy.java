package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.os.Build;
import android.widget.AbsSeekBar;

import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingy;
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.InputCapable;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebInputData;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Handles CSS generation for SeekBar and ProgressBar views.
 * Renders as an HTML input[type="range"] element with value, min, max attributes.
 */
public class SessionReplaySeekBarThingy extends SessionReplayViewThingy implements InputCapable {
    private final int currentValue;
    private final int minValue;
    private final int maxValue;
    private final ViewDetails viewDetails;

    public SessionReplaySeekBarThingy(ViewDetails viewDetails, AbsSeekBar view) {
        super(viewDetails);
        this.viewDetails = viewDetails;
        this.currentValue = view.getProgress();
        this.minValue = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? view.getMin() : 0;
        this.maxValue = view.getMax();
    }

    private Attributes buildAttributes() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.type = "range";
        attributes.inputType = "range";
        attributes.value = String.valueOf(currentValue);
        attributes.min = String.valueOf(minValue);
        attributes.max = String.valueOf(maxValue);
        attributes.step = "any";
        return attributes;
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        return new RRWebElementNode(
                buildAttributes(),
                RRWebElementNode.TAG_TYPE_INPUT,
                viewDetails.viewId,
                new ArrayList<>()
        );
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplaySeekBarThingy)) {
            return Collections.emptyList();
        }

        SessionReplaySeekBarThingy otherSeekBar = (SessionReplaySeekBarThingy) other;
        List<MutationRecord> parentDifferences = super.generateDifferences(other);
        boolean hasValueChange = this.currentValue != otherSeekBar.currentValue
                || this.minValue != otherSeekBar.minValue
                || this.maxValue != otherSeekBar.maxValue;

        if ((parentDifferences == null || parentDifferences.isEmpty()) && !hasValueChange) {
            return Collections.emptyList();
        }

        List<MutationRecord> mutations = new ArrayList<>();
        if (parentDifferences != null) {
            mutations.addAll(parentDifferences);
        }

        if (hasValueChange) {
            mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.viewId, otherSeekBar.buildAttributes()));
        }

        return mutations;
    }

    @Override
    public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
        RRWebElementNode viewNode = new RRWebElementNode(
                buildAttributes(),
                RRWebElementNode.TAG_TYPE_INPUT,
                viewDetails.viewId,
                new ArrayList<>()
        );

        viewNode.attributes.metadata.put("style", generateInlineCss());

        RRWebMutationData.AddRecord viewAddRecord = new RRWebMutationData.AddRecord(parentId, null, viewNode);
        return Collections.singletonList(viewAddRecord);
    }

    @Override
    public RRWebInputData generateInputData(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplaySeekBarThingy)) return null;
        SessionReplaySeekBarThingy otherSeekBar = (SessionReplaySeekBarThingy) other;
        if (currentValue == otherSeekBar.currentValue) return null;
        return new RRWebInputData(viewDetails.viewId, String.valueOf(otherSeekBar.currentValue), false);
    }

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        if (other == null || !(other instanceof SessionReplaySeekBarThingy)) {
            return true;
        }
        return this.hashCode() != other.hashCode();
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewDetails, currentValue, minValue, maxValue);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SessionReplaySeekBarThingy)) return false;

        SessionReplaySeekBarThingy that = (SessionReplaySeekBarThingy) other;
        return viewDetails.equals(that.viewDetails)
                && currentValue == that.currentValue
                && minValue == that.minValue
                && maxValue == that.maxValue;
    }
}