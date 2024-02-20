/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AnalyticsValidator {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private static final String NEW_RELIC_PREFIX = "newRelic";
    private static final String NR_PREFIX = "nr.";
    private static final String PUBLIC_PREFIX = "Public_";

    // Reminder: add updates from https://docs.newrelic.com/docs/insights/insights-data-sources/custom-data/insights-custom-data-requirements-limits#reserved-words
    static final Set<String> reservedAttributeNames = new HashSet<String>() {{
        add(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE);
        add(AnalyticsAttribute.TYPE_ATTRIBUTE);
        add(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE);
        add(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE);
        add(AnalyticsAttribute.ACCOUNT_ID_ATTRIBUTE);
        add(AnalyticsAttribute.APP_ID_ATTRIBUTE);
        add(AnalyticsAttribute.APP_NAME_ATTRIBUTE);
        add(AnalyticsAttribute.UUID_ATTRIBUTE);
        add(AnalyticsAttribute.SESSION_ID_ATTRIBUTE);
        add(AnalyticsAttribute.OS_NAME_ATTRIBUTE);
        add(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        add(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        add(AnalyticsAttribute.DEVICE_MANUFACTURER_ATTRIBUTE);
        add(AnalyticsAttribute.DEVICE_MODEL_ATTRIBUTE);
        add(AnalyticsAttribute.MEM_USAGE_MB_ATTRIBUTE);
        add(AnalyticsAttribute.CARRIER_ATTRIBUTE);
        add(AnalyticsAttribute.NEW_RELIC_VERSION_ATTRIBUTE);
        add(AnalyticsAttribute.INTERACTION_DURATION_ATTRIBUTE);
        add(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE);
        add(AnalyticsAttribute.APP_UPGRADE_ATTRIBUTE);
        add(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE);
        add(AnalyticsAttribute.APPLICATION_PLATFORM_VERSION_ATTRIBUTE);
        add(AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        add(AnalyticsAttribute.OS_BUILD_ATTRIBUTE);
        add(AnalyticsAttribute.RUNTIME_ATTRIBUTE);
        add(AnalyticsAttribute.ARCHITECTURE_ATTRIBUTE);
        add(AnalyticsAttribute.APP_BUILD_ATTRIBUTE);
    }};

    protected static Set<String> excludedAttributeNames = new HashSet<String>() {{
        add(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE);
        add(AnalyticsAttribute.APP_UPGRADE_ATTRIBUTE);
        add(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE);
    }};


    //
    // Attribute validations
    //

    /**
     * Check that the attribute name meets requirements
     * Attribute names can be a combination of alphanumeric characters, colons (:),
     * periods (.), and underscores (_).
     *
     * @param keyName
     * @return True if valid attribute name
     */
    boolean isValidKeyName(String keyName) {
        boolean valid = (keyName != null) && (!keyName.isEmpty()) && (keyName.length() < AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH);

        if (!valid) {
            log.error("Attribute name [" + keyName + "] is null, empty, or exceeds the maximum length of " +
                    AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH + " characters.");
        }

        return valid;
    }

    /**
     * Check that the attribute name meets requirements AND is not a reserved attribute name
     *
     * @param attributeName
     * @return True if valid attribute name
     */
    public boolean isValidAttributeName(String attributeName) {
        boolean valid = isValidKeyName(attributeName);

        if (valid) {
            valid = !isReservedAttributeName(attributeName);
            if (!valid) {
                log.error("Attribute name [" + attributeName + "] is reserved for internal use and will be ignored.");
            }
        }

        return valid;
    }

    public boolean isReservedAttributeName(String attributeName) {
        if (reservedAttributeNames.contains(attributeName)) {
            log.error("Attribute name [" + attributeName + "] is in the reserved names list.");
            return true;
        }

        if (attributeName.startsWith(NEW_RELIC_PREFIX)) {
            log.error("Attribute name [" + attributeName + "] starts with reserved prefix [" + NEW_RELIC_PREFIX + "]");
            return true;
        }

        if (attributeName.startsWith(NR_PREFIX)) {
            log.error("Attribute name [" + attributeName + "] starts with reserved prefix [" + NR_PREFIX + "]");
            return true;
        }

        if (attributeName.startsWith(PUBLIC_PREFIX)) {
            log.error("Attribute name [" + attributeName + "] starts with reserved prefix [" + PUBLIC_PREFIX + "]");
            return true;
        }

        return false;
    }

    public boolean isExcludedAttributeName(String attributeName) {
        return excludedAttributeNames.contains(attributeName);
    }

    /**
     * Check that the attribute value meets requirements
     */
    public boolean isValidAttributeValue(String name, String value) {
        boolean valid = (value != null) && !value.isEmpty() && (value.getBytes().length < AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH);

        if (!valid) {
            log.error("Attribute value for name [" + name + "] is null, empty, or exceeds the maximum length of " + AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH + " bytes.");
        }

        return valid;
    }

    public boolean isValidAttribute(final AnalyticsAttribute attribute) {
        return (attribute != null) &&
                isValidAttributeName(attribute.getName()) &&
                isValidAttributeValue(attribute.getName(), attribute.valueAsString());
    }

    public Set<AnalyticsAttribute> toValidatedAnalyticsAttributes(Map<String, Object> attributeMap) {
        try {
            Set<AnalyticsAttribute> filteredAttributes = new HashSet<AnalyticsAttribute>();
            for (String key : attributeMap.keySet()) {
                Object value = attributeMap.get(key);
                AnalyticsAttribute attr = AnalyticsAttribute.createAttribute(key, value);
                if (isValidAttribute(attr)) {
                    filteredAttributes.add(attr);
                } else {
                    log.warn(String.format("Attribute [" + key + "] ignored: value is null, empty or exceeds the maximum name length"));
                }
            }

            return filteredAttributes;

        } catch (Exception e) {
            log.error("Error occurred filtering attribute map: ", e);
        }

        return Collections.emptySet();
    }

    public Set<AnalyticsAttribute> toValidatedAnalyticsAttributes(Set<AnalyticsAttribute> attributeSet) {
        try {
            Set<AnalyticsAttribute> filteredAttributes = new HashSet<AnalyticsAttribute>();
            for (AnalyticsAttribute attribute : attributeSet) {
                if (isValidAttribute(attribute)) {
                    filteredAttributes.add(new AnalyticsAttribute(attribute));
                } else {
                    log.warn(String.format("Attribute [" + attribute.getName() + "] ignored: value is null, empty or exceeds the maximum name length"));
                }
            }

            return filteredAttributes;

        } catch (Exception e) {
            log.error("Error occurred filtering attribute set: ", e);
        }

        return Collections.emptySet();
    }


    //
    // Event validations
    //
    private static final String ALLOWABLE_EVENT_TYPE_CHARS = "^[\\p{L}\\p{Nd} _:.]+$";

    static final Set<String> reservedEventTypes = new HashSet<String>() {{
        add(AnalyticsEvent.EVENT_TYPE_MOBILE);
        add(AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST);
        add(AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR);
        add(AnalyticsEvent.EVENT_TYPE_MOBILE_BREADCRUMB);
        add(AnalyticsEvent.EVENT_TYPE_MOBILE_CRASH);
        add(AnalyticsEvent.EVENT_TYPE_MOBILE_USER_ACTION);
        add(AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT);
    }};

    /**
     * Event types (using the eventType attribute) can be a combination of alphanumeric characters,
     * colons (:), and underscores (_).
     *
     * @param eventName
     * @return trie if valid name
     */
    public boolean isValidEventName(String eventName) {
        return (eventName != null && !eventName.isEmpty() && eventName.length() < AnalyticsEvent.EVENT_NAME_MAX_LENGTH);
    }

    /**
     * Can be a combination of alphanumeric characters, colons (:), and underscores (_).
     * Event types starting with Metric, MetricRaw, and strings prefixed with Metric[0-9] (such
     * as Metric2 or Metric1Minute), Public_ and strings prefixed with Public_ are reserved.
     *
     */
    public boolean isValidEventType(String eventType) {
        boolean valid = (eventType != null);

        if (valid) {
            valid = eventType.matches(ALLOWABLE_EVENT_TYPE_CHARS);
        }

        if (!valid) {
            log.error("Event type [" + eventType + "] is invalid and will be ignored. " +
                    "Custom event types may only include alphanumeric, ' ', '.', ':' or '_' characters.");
        }

        return valid;
    }

    public boolean isReservedEventType(String eventType) {
        boolean reserved = isValidEventType(eventType) && reservedEventTypes.contains(eventType);

        if (reserved) {
            log.error("Event type [" + eventType + "] is reserved and will be ignored.");
        }

        return reserved;
    }

    public String toValidEventType(final String eventType) {
        return (eventType == null || eventType.isEmpty()) ? AnalyticsEvent.EVENT_TYPE_MOBILE : eventType;
    }

    public AnalyticsEventCategory toValidCategory(final AnalyticsEventCategory category) {
        return category == null ? AnalyticsEventCategory.Custom : category;
    }

}
