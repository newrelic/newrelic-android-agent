/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import java.util.Map;

/**
 * Parameters for a single {@link MobileViewContext#onViewAppeared(MobileViewAppearance)} call.
 * A builder rather than a growing positional-argument list, since the MobileView schema (IDD
 * NR-356639 §5.3) has more appear-side fields than any producer needs at once, and future
 * batches (MobileViewTiming, custom-attribute capabilities) are expected to add more.
 */
public class MobileViewAppearance {
    private final String viewName;
    private String viewClass;
    private UiPlatform uiPlatform;
    private Map<String, Object> attributes;
    private Long loadTimeMs;

    public MobileViewAppearance(String viewName, UiPlatform uiPlatform) {
        this.viewName = viewName;
        this.uiPlatform = uiPlatform;
    }

    public MobileViewAppearance viewClass(String viewClass) {
        this.viewClass = viewClass;
        return this;
    }

    public MobileViewAppearance attributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }

    public MobileViewAppearance loadTimeMs(Long loadTimeMs) {
        this.loadTimeMs = loadTimeMs;
        return this;
    }

    public String getViewName() {
        return viewName;
    }

    public String getViewClass() {
        return viewClass;
    }

    public UiPlatform getUiPlatform() {
        return uiPlatform;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Long getLoadTimeMs() {
        return loadTimeMs;
    }
}
