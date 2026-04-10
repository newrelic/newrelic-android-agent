package com.newrelic.agent.android.sessionReplay.models.IncrementalEvent;

public class RRWebInputData extends RRWebIncrementalData {
    public int id;
    public String text;
    public boolean isChecked;

    public RRWebInputData(int id, String text, boolean isChecked) {
        this.source = RRWebIncrementalSource.INPUT;
        this.id = id;
        this.text = text;
        this.isChecked = isChecked;
    }
}
