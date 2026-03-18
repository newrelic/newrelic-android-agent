package com.newrelic.agent.android.sessionReplay.models.IncrementalEvent;

import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface;

/**
 * Interface for thingies that represent input elements (radio, checkbox, slider, text input).
 * Implementations generate rrweb input events (source=5) when input state changes.
 */
public interface InputCapable {
    /**
     * Generate an rrweb input event when input state changes between old and new versions.
     *
     * @param other The new version of this view to compare against
     * @return RRWebInputData if input state changed, null otherwise
     */
    RRWebInputData generateInputData(SessionReplayViewThingyInterface other);
}
