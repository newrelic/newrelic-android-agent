package com.newrelic.agent.android.sessionReplay;

import android.view.View;
import android.view.ViewGroup;
import androidx.compose.ui.platform.ComposeView;
import com.newrelic.agent.android.R;
import com.newrelic.agent.android.util.ComposeChecker;

import java.util.Set;

public class ViewTouchHandler {
    private final SessionReplayConfiguration sessionReplayConfiguration;

    public ViewTouchHandler(SessionReplayConfiguration sessionReplayConfiguration) {
        this.sessionReplayConfiguration = sessionReplayConfiguration;
    }

    public Object findViewAtCoords(View rootView, int x, int y) {
        if (rootView == null) {
            return null;
        }

        // Check if the touch coordinates are within the bounds of the root view
        if (!isViewContainsPoint(rootView, x, y)) {
            return null;
        }

        if (!(rootView instanceof ViewGroup)) {
            // If it's not a ViewGroup, return the view itself
            return rootView;
        }

        // If it's a ViewGroup, search its children
        ViewGroup viewGroup = (ViewGroup) rootView;
        for (int i = viewGroup.getChildCount() - 1; i >= 0; i--) {
            View child = viewGroup.getChildAt(i);
            Object foundView = findViewAtCoords(child, x, y);
            if (foundView != null) {

                if(ComposeChecker.isComposeUsed(rootView.getContext())  && (foundView instanceof View && ((View)foundView).getParent() instanceof ComposeView)) {
                    // Delegate to SemanticsNode handler for Compose views
                    return foundView; // Return the compose view, let caller handle semantics
                }
                return foundView;
            }
        }

        // If no child views contain the point, return the parent
        return rootView;
    }

    public int getViewStableId(View view) {
        if(view == null) {
            return -1;
        }
        int keyCode = "NewRelicSessionReplayViewId".hashCode();
        Integer idValue = (Integer) view.getTag(keyCode);
        if(idValue == null) {
            idValue = NewRelicIdGenerator.generateId();
            view.setTag(keyCode, idValue);
        }
        return idValue;
    }

    public View getMaskedViewIfNeeded(View view, boolean shouldMask) {
        if(view != null) {
            // Check if view has tags that prevent masking
            Object viewTag = view.getTag();
            Object privacyTag = view.getTag(R.id.newrelic_privacy);
            boolean hasUnmaskTag = ("nr-unmask".equals(viewTag)) ||
                    ("nr-unmask".equals(privacyTag)) ||
                    (view.getTag() != null && sessionReplayConfiguration.shouldUnmaskViewTag(view.getTag().toString())) ||
                    checkMaskUnMaskViewClass(sessionReplayConfiguration.getUnmaskedViewClasses(), view);

            // Check if view has tag that forces masking
            boolean hasMaskTag = ("nr-mask".equals(viewTag) || "nr-mask".equals(privacyTag)) ||
                    (view.getTag() != null && sessionReplayConfiguration.shouldMaskViewTag(view.getTag().toString())) ||
                    checkMaskUnMaskViewClass(sessionReplayConfiguration.getMaskedViewClasses(), view);

            // Apply masking if needed:
            // - If general masking is enabled AND no unmask tag AND not in unmask class list, OR
            // - If has explicit mask tag OR class is explicitly masked
            if ((shouldMask && !hasUnmaskTag) || (!shouldMask && hasMaskTag)) {
                return null;
            }

            // Return original view if no masking needed
            return view;
        }
        return null;
    }

    private boolean isViewContainsPoint(View view, int x, int y) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int left = location[0];
        int top = location[1];
        int right = left + view.getWidth();
        int bottom = top + view.getHeight();
        return (x >= left && x <= right && y >= top && y <= bottom);
    }

    private boolean checkMaskUnMaskViewClass(Set<String> viewClasses, View view) {
        Class clazz = view.getClass();

        while (clazz != null) {
            if (viewClasses.contains(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}