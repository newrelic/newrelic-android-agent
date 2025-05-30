package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.widget.EditText;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionReplayEditTextThingy extends SessionReplayTextViewThingy implements SessionReplayViewThingyInterface {
    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();
    private ViewDetails viewDetails;

    public boolean shouldRecordSubviews = false;
    private String hint;
    public SessionReplayEditTextThingy(ViewDetails viewDetails, EditText view, MobileSessionReplayConfiguration sessionReplayConfiguration) {
        super(viewDetails, view, sessionReplayConfiguration);
        this.viewDetails = viewDetails;

        this.hint = view.getHint() != null ? view.getHint().toString() : "";

        if (sessionReplayConfiguration.isMaskApplicationText() || sessionReplayConfiguration.isMaskUserInputText()) {
            // Replace all characters with asterisks (*) to mask the hint text
            if (!this.hint.isEmpty()) {
                StringBuilder maskedHint = new StringBuilder();
                for (int i = 0; i < this.hint.length(); i++) {
                    maskedHint.append('*');
                }
                this.hint = maskedHint.toString();
            }
        }
    }

    @Override
    public List<? extends SessionReplayViewThingyInterface> getSubviews() {
        return subviews;
    }

    @Override
    public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
        this.subviews = subviews;
    }

    @Override
    public boolean shouldRecordSubviews() {
        return shouldRecordSubviews;
    }

    @Override
    public String getCSSSelector() {
        return viewDetails.getCssSelector();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String generateCssDescription() {

        StringBuilder cssBuilder = new StringBuilder(super.generateCssDescription());
        return cssBuilder.toString();
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        RRWebTextNode textNode;
        if (super.getLabelText().isEmpty() && !hint.isEmpty()) {
            textNode = new RRWebTextNode(hint, false, NewRelicIdGenerator.generateId());
        } else {
            textNode = new RRWebTextNode(super.getLabelText(), false, NewRelicIdGenerator.generateId());
        }

        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.setType("text"); // Set input type to "text" for EditText

        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(), Collections.singletonList(textNode));
    }
}