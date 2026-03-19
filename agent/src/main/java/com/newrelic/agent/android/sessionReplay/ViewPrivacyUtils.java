package com.newrelic.agent.android.sessionReplay;

import android.view.View;
import android.view.ViewParent;

import com.newrelic.agent.android.R;
import com.newrelic.agent.android.sessionReplay.compose.ComposeSessionReplayConstants;

/**
 * Shared privacy tag utilities for Android Views.
 * Centralizes tag detection logic used by capture and touch handling.
 */
public final class ViewPrivacyUtils {

    private ViewPrivacyUtils() {}

    /**
     * Returns the effective privacy tag for a view, checking both
     * {@code R.id.newrelic_privacy} and the general tag.
     */
    public static String getEffectivePrivacyTag(View view) {

        Object generalTag = view.getTag();
        if (generalTag instanceof String) {
            return (String) generalTag;
        }

        Object privacyTag = view.getTag(R.id.newrelic_privacy);
        if (privacyTag instanceof String) {
            return (String) privacyTag;
        }

        return "";
    }

    public static boolean isBlocked(View view) {
        return ComposeSessionReplayConstants.PrivacyTags.BLOCK.equals(getEffectivePrivacyTag(view));
    }

    public static boolean isMasked(View view) {
        return ComposeSessionReplayConstants.PrivacyTags.MASK.equals(getEffectivePrivacyTag(view));
    }

    public static boolean isUnmasked(View view) {
        return ComposeSessionReplayConstants.PrivacyTags.UNMASK.equals(getEffectivePrivacyTag(view));
    }

    /**
     * Walks up the View hierarchy to check if any ancestor is blocked.
     */
    public static boolean hasBlockedAncestor(View view) {
        View current = view;
        while (current != null) {
            if (isBlocked(current)) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = (parent instanceof View) ? (View) parent : null;
        }
        return false;
    }
}
