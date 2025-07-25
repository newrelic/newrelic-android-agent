package com.newrelic.agent.android.sessionReplay.internal;

import androidx.annotation.NonNull;

import com.newrelic.agent.android.sessionReplay.SessionReplayFrame;

public interface OnFrameTakenListener {
    void onFrameTaken(@NonNull SessionReplayFrame newFrame);
}
