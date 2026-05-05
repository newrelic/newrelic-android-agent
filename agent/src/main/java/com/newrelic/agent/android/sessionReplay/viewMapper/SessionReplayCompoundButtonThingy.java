package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.ToggleButton;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.internal.NewRelicIdGenerator;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.InputCapable;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebInputData;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles CSS generation for CompoundButton subclasses (RadioButton, CheckBox, Switch, ToggleButton).
 * Renders as an HTML input element with type="radio" or type="checkbox" and tracks checked state.
 */
public class SessionReplayCompoundButtonThingy extends SessionReplayTextViewThingy implements InputCapable {
    private final boolean isChecked;
    private final boolean isRadioButton;
    private final boolean isToggle;
    private final ViewDetails viewDetails;
    private final int inputNodeId;
    private final int textNodeId;

    public SessionReplayCompoundButtonThingy(ViewDetails viewDetails, CompoundButton view, AgentConfiguration agentConfiguration) {
        super(viewDetails, view, agentConfiguration);
        this.viewDetails = viewDetails;
        this.isChecked = view.isChecked();
        this.isRadioButton = view instanceof RadioButton;
        this.isToggle = view instanceof Switch || view instanceof ToggleButton || view.getClass().getName().contains("Switch");
        this.inputNodeId = getStableNodeId(view, "NewRelicSessionReplayInputNodeId");
        this.textNodeId = getStableNodeId(view, "NewRelicSessionReplayTextNodeId");
    }

    private static int getStableNodeId(CompoundButton view, String tagName) {
        int keyCode = tagName.hashCode();
        Integer idValue = (Integer) view.getTag(keyCode);
        if (idValue == null) {
            idValue = NewRelicIdGenerator.generateId();
            view.setTag(keyCode, idValue);
        }
        return idValue;
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        // Create the <input type="checkbox|radio"> element

        Attributes inputAttrs = new Attributes("");
        if (isChecked) {
            inputAttrs.checked = true;
        }
        if (isToggle) {
            inputAttrs.dataNrType = "toggle";
        } else {
            String inputType = isRadioButton ? "radio" : "checkbox";
            inputAttrs.type = inputType;
            inputAttrs.inputType = inputType;
        }
        RRWebElementNode inputNode = new RRWebElementNode(
                inputAttrs,
                RRWebElementNode.TAG_TYPE_INPUT,
                inputNodeId,
                new ArrayList<>()
        );

        // Create the text node for the label
        RRWebTextNode textNode = new RRWebTextNode(getLabelText(), false, textNodeId);

        // Wrap in a <label> so both the input indicator and text are visible
        Attributes labelAttrs = new Attributes(viewDetails.getCssSelector());
        ArrayList<RRWebNode> children = new ArrayList<>();
        children.add(inputNode);
        children.add(textNode);
        return new RRWebElementNode(
                labelAttrs,
                RRWebElementNode.TAG_TYPE_LABEL,
                viewDetails.viewId,
                children
        );
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplayCompoundButtonThingy)) {
            return Collections.emptyList();
        }

        SessionReplayCompoundButtonThingy otherButton = (SessionReplayCompoundButtonThingy) other;
        List<MutationRecord> parentDifferences = super.generateDifferences(other);
        boolean hasCheckedChange = this.isChecked != otherButton.isChecked;

        if ((parentDifferences == null || parentDifferences.isEmpty()) && !hasCheckedChange) {
            return Collections.emptyList();
        }

        List<MutationRecord> mutations = new ArrayList<>();
        if (parentDifferences != null) {
            mutations.addAll(parentDifferences);
        }

        if (hasCheckedChange) {
            Attributes attributes = new Attributes("");
            if (otherButton.isChecked) {
                attributes.checked = true;
            }
            if (isToggle) {
                attributes.dataNrType = "toggle";
            } else {
                String inputType = isRadioButton ? "radio" : "checkbox";
                attributes.type = inputType;
                attributes.inputType = inputType;
            }
            mutations.add(new RRWebMutationData.AttributeRecord(inputNodeId, attributes));
        }

        return mutations;
    }

    @Override
    public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
        // Create the <input> element
        String inputType = isRadioButton ? "radio" : "checkbox";
        Attributes inputAttrs = new Attributes("");
        if (isChecked) {
            inputAttrs.checked = true;
        }
        if (isToggle) {
            inputAttrs.dataNrType = "toggle";
        } else {
            inputAttrs.type = inputType;
            inputAttrs.inputType = inputType;
        }
        RRWebElementNode inputNode = new RRWebElementNode(
                inputAttrs,
                RRWebElementNode.TAG_TYPE_INPUT,
                inputNodeId,
                new ArrayList<>()
        );

        // Create the text node
        RRWebTextNode textNode = new RRWebTextNode(getLabelText(), false, textNodeId);

        // Create the <label> wrapper
        Attributes labelAttrs = new Attributes(viewDetails.getCssSelector());
        ArrayList<RRWebNode> children = new ArrayList<>();
        children.add(inputNode);
        children.add(textNode);
        RRWebElementNode labelNode = new RRWebElementNode(
                labelAttrs,
                RRWebElementNode.TAG_TYPE_LABEL,
                viewDetails.viewId,
                children
        );
        labelNode.attributes.metadata.put("style", generateInlineCss());

        List<RRWebMutationData.AddRecord> adds = new ArrayList<>();
        adds.add(new RRWebMutationData.AddRecord(parentId, null, labelNode));
        adds.add(new RRWebMutationData.AddRecord(viewDetails.viewId, null, inputNode));
        adds.add(new RRWebMutationData.AddRecord(viewDetails.viewId, null, textNode));
        return adds;
    }

    @Override
    public RRWebInputData generateInputData(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplayCompoundButtonThingy)) return null;
        SessionReplayCompoundButtonThingy otherButton = (SessionReplayCompoundButtonThingy) other;
        if (isChecked == otherButton.isChecked) return null;
        return new RRWebInputData(inputNodeId, "", otherButton.isChecked);
    }

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplayCompoundButtonThingy)) {
            return true;
        }
        return this.hashCode() != other.hashCode();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (isChecked ? 1 : 0);
        result = 31 * result + (isToggle ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SessionReplayCompoundButtonThingy)) return false;

        SessionReplayCompoundButtonThingy that = (SessionReplayCompoundButtonThingy) other;
        return viewDetails.equals(that.viewDetails) && isChecked == that.isChecked && isToggle == that.isToggle;
    }
}