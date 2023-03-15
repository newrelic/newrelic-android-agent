/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.agentdata.AgentDataController;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventTransformAdapter implements EventListener {
    final String REPLACEMENT = "*";
    final Map<String, Map<Pattern, String>> attributeTransforms = new HashMap<>();

    EventManagerImpl em = null;

    @Override
    public boolean onEventAdded(AnalyticsEvent analyticsEvent) {
        return (em != null ? em.onEventAdded(analyticsEvent) : true);
    }

    @Override
    public boolean onEventOverflow(AnalyticsEvent analyticsEvent) {
        return (em != null) ? em.onEventOverflow(analyticsEvent) : false;
    }

    @Override
    public boolean onEventEvicted(AnalyticsEvent analyticsEvent) {
        return (em != null) ? em.onEventEvicted(analyticsEvent) : true;
    }

    @Override
    public void onEventQueueSizeExceeded(int queueSize) {
        if (em != null) {
            em.onEventQueueSizeExceeded(queueSize);
        }
    }

    @Override
    public void onEventQueueTimeExceeded(int queueTime) {
        if (em != null) {
            em.onEventQueueTimeExceeded(queueTime);
        }
    }

    @Override
    public void onEventFlush() {
        if (em != null) {
            em.onEventFlush();
        }
    }

    @Override
    public void onStart(EventManager eventManager) {
        this.em = (EventManagerImpl) eventManager;
        if (this.em != null) {
            this.em.onStart(eventManager);
        }
    }

    @Override
    public void onShutdown() {
        if (em != null) {
            em.onShutdown();
            em = null;
        }
    }

    public EventTransformAdapter withAttributeTransform(final String attributeName, final Map<String, String> transforms) {
        Map<Pattern, String> attributeTransform = attributeTransforms.get(attributeName);

        if (attributeTransform == null) {
            attributeTransform = new HashMap<>();
        }

        if (transforms != null) {
            for (final HashMap.Entry<String, String> entry : transforms.entrySet()) {
                try {
                    Pattern pattern = Pattern.compile(entry.getKey());
                    attributeTransform.put(pattern, entry.getValue());

                } catch (Exception e) {
                    AgentDataController.sendAgentData(e, new HashMap<String, Object>() {{
                        put("transform", entry.getKey() + "/" + entry.getValue());
                    }});
                }
            }
        }

        if (!attributeTransform.isEmpty()) {
            attributeTransforms.put(attributeName, attributeTransform);
        }

        return this;
    }

    public void onEventTransform(AnalyticsEvent analyticsEvent) {
        for (AnalyticsAttribute analyticsAttribute : analyticsEvent.getAttributeSet()) {
            if (attributeTransforms.containsKey(analyticsAttribute.getName())) {
                final String transformedValue = onAttributeTransform(analyticsAttribute.getName(), analyticsAttribute.getStringValue());
                analyticsAttribute.setStringValue(transformedValue);
            }
        }
    }

    public String onAttributeTransform(String attributeName, String attributeValue) {
        final Map<Pattern, String> transforms = attributeTransforms.get(attributeName);
        if (transforms != null) {
            for (Pattern pattern : transforms.keySet()) {
                attributeValue = onPatternTransform(pattern, transforms.get(pattern), attributeValue);
            }
        }

        return attributeValue;
    }

    String onPatternTransform(Pattern pattern, String replacement, String value) {
        final Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            final StringBuilder transformedValue = new StringBuilder();
            if (replacement == null) {
                transformedValue.append(value);
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    for (int j = matcher.start(i); j < matcher.end(i); j++) {
                        transformedValue.replace(j, j + 1, REPLACEMENT);
                    }
                }
            } else {
                transformedValue.append(matcher.replaceAll(replacement));
            }

            return transformedValue.toString();
        }

        return value;
    }
}
