/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.internal;

import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

/**
 * Utility class for TextView operations
 * Handles text color extraction and React Native TextView detection
 */
public class TextViewUtil {

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String TAG = "TextViewUtil";

    /**
     * Extracts the first ForegroundColorSpan color from a Spannable text.
     * React Native uses ForegroundColorSpan to apply text colors within styled text.
     *
     * @param spannable The Spannable to extract the color from
     * @return The color as an Integer (ARGB format), or null if no color span found
     */
    public static Integer getFirstForegroundColorFromSpannable(Spannable spannable) {
        if (spannable == null) {
            return null;
        }

        try {
            // Get all ForegroundColorSpan spans from the text
            ForegroundColorSpan[] colorSpans = spannable.getSpans(
                0,
                spannable.length(),
                ForegroundColorSpan.class
            );

            if (colorSpans != null && colorSpans.length > 0) {
                // Return the color from the first span
                int color = colorSpans[0].getForegroundColor();
                log.debug(TAG + ": Found " + colorSpans.length + " ForegroundColorSpan(s), using first color: " + String.format("#%08X", color));
                return color;
            }
        } catch (Exception e) {
            log.error(TAG + ": Error extracting ForegroundColorSpan: " + e.getMessage());
        }

        return null;
    }

    /**
     * Checks if a TextView is a React Native TextView.
     * This checks if the class name matches ReactTextView.
     *
     * @param textView The TextView to check
     * @return true if it's a ReactTextView, false otherwise
     */
    public static boolean isReactNativeTextView(TextView textView) {
        if (textView == null) {
            return false;
        }

        String className = textView.getClass().getName();
        return className.equals("com.facebook.react.views.text.ReactTextView");
    }

    /**
     * Extracts the text color from a TextView, with special handling for React Native TextViews.
     * For React Native TextViews, it attempts to extract color from ForegroundColorSpan in the Spannable.
     * Falls back to getCurrentTextColor() if extraction fails or not a React Native TextView.
     *
     * @param textView The TextView to extract the color from
     * @return The color as an integer (ARGB format)
     */
    public static int getTextColor(TextView textView) {
        if (textView == null) {
            return 0xFF000000; // Default black
        }

        // For React Native TextViews, try to get color from ForegroundColorSpan first
        if (isReactNativeTextView(textView)) {
            Spannable spannable = ReflectionUtils.getReactNativeSpannable(textView);
            if (spannable != null) {
                Integer extractedColor = getFirstForegroundColorFromSpannable(spannable);
                if (extractedColor != null) {
                    return extractedColor;
                }
            }
        }

        // Fall back to standard TextView color
        return textView.getCurrentTextColor();
    }

    /**
     * Converts a color integer to a hex string (RGB only, no alpha).
     *
     * @param color The color integer (ARGB format)
     * @return The hex string representation (e.g., "ff5733")
     */
    public static String colorToRgbHex(int color) {
        return String.format("%06x", color & 0xFFFFFF);
    }
}