package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode; // Assuming ElementNodeData maps to this

import java.util.List;

public interface SessionReplayViewThingyInterface {

    ViewDetails getViewDetails();

    boolean shouldRecordSubviews();

    List<? extends SessionReplayViewThingyInterface> getSubviews();

    void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews);

    String generateCssDescription();

    String generateInlineCss();

    String getCssSelector();

    RRWebElementNode generateRRWebNode();

    List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other);

    int getViewId();

    @Override
    int hashCode();

    @Override
    boolean equals(Object obj);


}