/**
 * Copyright 2023-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

/**
 * Defines the text masking strategies available for Session Replay.
 * <p>
 * These strategies control how text is masked in captured screens:
 * <ul>
 *   <li>MASK_ALL_TEXT: Masks all text in the application, regardless of source or context</li>
 *   <li>MASK_USER_INPUT_TEXT: Only masks text that was input by the user (e.g., text fields, search bars)</li>
 *   <li>MASK_NO_TEXT: No masking is applied, all text is captured as-is</li>
 * </ul>
 */
public enum TextMaskingStrategy {
    /**
     * Masks all text in the application, regardless of source or context.
     * This provides the highest level of privacy protection.
     */
    MASK_ALL_TEXT,

    /**
     * Only masks text that was input by the user.
     * Examples include text fields, search bars, and other input elements.
     * Application-generated text remains visible.
     */
    MASK_USER_INPUT_TEXT,

    /**
     * No masking is applied. All text is captured as-is.
     * This provides the most detailed session replay but with lowest privacy protection.
     */
    MASK_NO_TEXT
}