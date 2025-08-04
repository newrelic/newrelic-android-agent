package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode; // Assuming ElementNodeData maps to this

import java.util.List;

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

    // Classes implementing this interface must override hashCode() and equals()
    @Override
    int hashCode();

    @Override
    boolean equals(Object obj);
}