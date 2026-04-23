package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.view.View;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Handles rendering of Material Slider views (com.google.android.material.slider.BaseSlider)
 * in session replay. Uses reflection since the agent doesn't depend on Material library.
 * Renders as {@code <input type="range" value="X" min="Y" max="Z">}.
 */
public class SessionReplaySliderThingy extends SessionReplayViewThingy {
    private final float value;
    private final float valueFrom;
    private final float valueTo;
    private final ViewDetails viewDetails;

    public SessionReplaySliderThingy(ViewDetails viewDetails, View sliderView) {
        super(viewDetails);
        this.viewDetails = viewDetails;
        this.value = getFloatViaReflection(sliderView, "getValue", 0f);
        this.valueFrom = getFloatViaReflection(sliderView, "getValueFrom", 0f);
        this.valueTo = getFloatViaReflection(sliderView, "getValueTo", 100f);
    }

    private static float getFloatViaReflection(View view, String methodName, float defaultValue) {
        try {
            Method method = view.getClass().getMethod(methodName);
            Object result = method.invoke(view);
            if (result instanceof Float) {
                return (Float) result;
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    /**
     * Checks whether the given view is a Material BaseSlider subclass.
     */
    public static boolean isSlider(View view) {
        Class<?> clazz = view.getClass();
        while (clazz != null) {
            if ("com.google.android.material.slider.BaseSlider".equals(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private Attributes buildAttributes() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.type = "range";
        attributes.inputType = "range";
        attributes.value = String.valueOf((int) value);
        attributes.min = String.valueOf((int) valueFrom);
        attributes.max = String.valueOf((int) valueTo);
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
        if (!(other instanceof SessionReplaySliderThingy)) {
            return Collections.emptyList();
        }

        SessionReplaySliderThingy otherSlider = (SessionReplaySliderThingy) other;
        List<MutationRecord> parentDifferences = super.generateDifferences(other);
        boolean hasValueChange = this.value != otherSlider.value
                || this.valueFrom != otherSlider.valueFrom
                || this.valueTo != otherSlider.valueTo;

        if ((parentDifferences == null || parentDifferences.isEmpty()) && !hasValueChange) {
            return Collections.emptyList();
        }

        List<MutationRecord> mutations = new ArrayList<>();
        if (parentDifferences != null) {
            mutations.addAll(parentDifferences);
        }

        if (hasValueChange) {
            mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.viewId, otherSlider.buildAttributes()));
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

        return Collections.singletonList(new RRWebMutationData.AddRecord(parentId, null, viewNode));
    }

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        if (other == null || !(other instanceof SessionReplaySliderThingy)) {
            return true;
        }
        return this.hashCode() != other.hashCode();
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewDetails, value, valueFrom, valueTo);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SessionReplaySliderThingy)) return false;

        SessionReplaySliderThingy that = (SessionReplaySliderThingy) other;
        return viewDetails.equals(that.viewDetails)
                && value == that.value
                && valueFrom == that.valueFrom
                && valueTo == that.valueTo;
    }
}