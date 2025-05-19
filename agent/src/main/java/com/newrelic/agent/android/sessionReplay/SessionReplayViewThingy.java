package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionReplayViewThingy implements SessionReplayViewThingyInterface {
    ViewDetails viewDetails;

    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();

    public SessionReplayViewThingy(ViewDetails viewDetails) {
        this.viewDetails = viewDetails;
    }

    @Override
    public boolean shouldRecordSubviews() {
        return true;
    }

    @Override
    public List<? extends SessionReplayViewThingyInterface> getSubviews() {
        return this.subviews;
    }

    @Override
    public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
        this.subviews = subviews;
    }

    @Override
    public String getCSSSelector() {
        return viewDetails.getCssSelector();
    }

    @Override
    public String generateCssDescription() {
        return viewDetails.generateCssDescription() + "}";
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attribues = new Attributes(viewDetails.getCSSSelector());
        return new RRWebElementNode(attribues, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(),
                new ArrayList<RRWebNode>());
    }

}
