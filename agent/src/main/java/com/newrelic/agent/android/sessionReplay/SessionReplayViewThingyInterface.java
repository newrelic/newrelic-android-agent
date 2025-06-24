package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode; // Assuming ElementNodeData maps to this
//import com.newrelic.agent.android.sessionReplay.models.MutationRecord; // Assuming MutationRecord exists in your models

import java.util.List;

// Equivalent to Swift's SessionReplayViewThingy protocol
// Implements Hashable equivalent via hashCode and equals
public interface SessionReplayViewThingyInterface {

    ViewDetails getViewDetails();

    // Equivalent to var shouldRecordSubviews: Bool { get }
    boolean shouldRecordSubviews();

    List<? extends SessionReplayViewThingyInterface> getSubviews();

    void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews);

    String generateCssDescription();

    String generateInlineCss();

    String getCssSelector();


    RRWebElementNode generateRRWebNode();

    List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other);

    int getViewId();

//    List<MutationRecord> generateDifferences(SessionReplayViewThingy other);

    // Equivalent to Swift's Hashable
    // Classes implementing this interface must override hashCode() and equals()
    @Override
    int hashCode();

    @Override
    boolean equals(Object obj);


}