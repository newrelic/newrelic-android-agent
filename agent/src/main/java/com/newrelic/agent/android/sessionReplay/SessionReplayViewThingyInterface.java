package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode; // Assuming ElementNodeData maps to this
//import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord; // Assuming MutationRecord exists in your models

import java.util.List;
import java.util.Objects; // For hashCode and equals equivalence

// Equivalent to Swift's SessionReplayViewThingy protocol
// Implements Hashable equivalent via hashCode and equals
public interface SessionReplayViewThingyInterface {

    // Equivalent to var viewDetails: ViewDetails { get }
//    ViewDetails getViewDetails();

    // Equivalent to var shouldRecordSubviews: Bool { get }
    boolean shouldRecordSubviews();

    List<? extends SessionReplayViewThingyInterface> getSubviews();

    void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews);

    String generateCssDescription();

    String getCSSSelector();

    // Equivalent to func generateRRWebNode() -> ElementNodeData
    // Assuming ElementNodeData maps to RRWebElementNode in your project
    RRWebElementNode generateRRWebNode();

    int getViewId();

//    List<MutationRecord> generateDifferences(SessionReplayViewThingy other);

    // Equivalent to Swift's Hashable
    // Classes implementing this interface must override hashCode() and equals()
    @Override
    int hashCode();

    @Override
    boolean equals(Object obj);
}