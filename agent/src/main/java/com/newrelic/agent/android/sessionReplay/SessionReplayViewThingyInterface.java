package com.newrelic.agent.android.sessionReplay;


import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode; // Assuming ElementNodeData maps to this

import java.util.List;

public interface SessionReplayViewThingyInterface {

    Object getViewDetails();

    boolean shouldRecordSubviews();

    List<? extends SessionReplayViewThingyInterface> getSubviews();

    void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews);

    String generateCssDescription();

    String generateInlineCss();

    String getCssSelector();

    RRWebElementNode generateRRWebNode();

    List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other);

    List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId);

    int getViewId();

    int getParentViewId();
    @Override
    int hashCode();

    @Override
    boolean equals(Object obj);

}