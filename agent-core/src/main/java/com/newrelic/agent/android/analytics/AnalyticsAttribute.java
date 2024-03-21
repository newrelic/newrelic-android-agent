/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class AnalyticsAttribute {

    public enum AttributeDataType {
        VOID,
        STRING,
        DOUBLE,
        BOOLEAN
    }

    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String ACCOUNT_ID_ATTRIBUTE = "accountId";
    public static final String APP_ID_ATTRIBUTE = "appId";
    public static final String APP_BUILD_ATTRIBUTE = "appBuild";
    public static final String APP_NAME_ATTRIBUTE = "appName";
    public static final String APPLICATION_PLATFORM_ATTRIBUTE = "platform";
    public static final String APPLICATION_PLATFORM_VERSION_ATTRIBUTE = "platformVersion";
    public static final String UUID_ATTRIBUTE = "uuid";
    public static final String OS_NAME_ATTRIBUTE = "osName";
    public static final String OS_VERSION_ATTRIBUTE = "osVersion";
    public static final String OS_MAJOR_VERSION_ATTRIBUTE = "osMajorVersion";
    public static final String OS_BUILD_ATTRIBUTE = "osBuild";
    public static final String ARCHITECTURE_ATTRIBUTE = "architecture";
    public static final String RUNTIME_ATTRIBUTE = "runTime";
    public static final String DEVICE_MANUFACTURER_ATTRIBUTE = "deviceManufacturer";
    public static final String DEVICE_MODEL_ATTRIBUTE = "deviceModel";
    public static final String CARRIER_ATTRIBUTE = "carrier";
    public static final String NEW_RELIC_VERSION_ATTRIBUTE = "newRelicVersion";
    public static final String MEM_USAGE_MB_ATTRIBUTE = "memUsageMb";
    public static final String SESSION_ID_ATTRIBUTE = "sessionId";
    public static final String SESSION_DURATION_ATTRIBUTE = "sessionDuration";
    public static final String SESSION_TIME_SINCE_LOAD_ATTRIBUTE = "timeSinceLoad";
    public static final String INTERACTION_DURATION_ATTRIBUTE = "interactionDuration";
    public static final String LAST_INTERACTION_ATTRIBUTE = "lastInteraction";
    public static final String MUTABLE = "mutable";
    public static final String UNHANDLED_NATIVE_EXCEPTION = "unhandledNativeException";
    public static final String NATIVE_CRASH = "nativeCrash";
    public static final String ANR = "ANR";

    public static final String EVENT_CATEGORY_ATTRIBUTE = "category";
    public static final String EVENT_NAME_ATTRIBUTE = "name";
    public static final String EVENT_TIMESTAMP_ATTRIBUTE = "timestamp";
    public static final String EVENT_TYPE_ATTRIBUTE = "eventType";

    public static final String TYPE_ATTRIBUTE = "type";

    public static final String APP_INSTALL_ATTRIBUTE = "install";
    public static final String APP_UPGRADE_ATTRIBUTE = "upgradeFrom";

    public static final String REQUEST_URL_ATTRIBUTE = "requestUrl";
    public static final String REQUEST_DOMAIN_ATTRIBUTE = "requestDomain";
    public static final String REQUEST_PATH_ATTRIBUTE = "requestPath";
    public static final String REQUEST_METHOD_ATTRIBUTE = "requestMethod";
    public static final String CONNECTION_TYPE_ATTRIBUTE = "connectionType";
    public static final String STATUS_CODE_ATTRIBUTE = "statusCode";
    public static final String BYTES_RECEIVED_ATTRIBUTE = "bytesReceived";
    public static final String BYTES_SENT_ATTRIBUTE = "bytesSent";
    public static final String RESPONSE_TIME_ATTRIBUTE = "responseTime";
    public static final String NETWORK_ERROR_CODE_ATTRIBUTE = "networkErrorCode";
    public static final String CONTENT_TYPE_ATTRIBUTE = "contentType";

    public static final String RESPONSE_BODY_ATTRIBUTE = "nr.responseBody";
    public static final String APP_DATA_ATTRIBUTE = "nr.X-NewRelic-App-Data";

    public static final String INSTANT_APP_ATTRIBUTE = "instantApp";

    // UserActions
    public static final String ACTION_TYPE_ATTRIBUTE = "actionType";

    public static final int ATTRIBUTE_NAME_MAX_LENGTH = 255;

    // For attributes attached to custom events sent using the Event API:
    public static final int ATTRIBUTE_VALUE_MAX_LENGTH = 4096;

    //Offline Storage
    public static final String OFFLINE_ATTRIBUTE_NAME = "offline";

    private static final AgentLog log = AgentLogManager.getAgentLog();    
    private final static AnalyticsValidator validator = new AnalyticsValidator();

    private String name;
    private String stringValue;
    private double doubleValue;
    private boolean isPersistent;
    private AttributeDataType attributeDataType;

    protected AnalyticsAttribute() {
        this.stringValue = null;
        this.doubleValue = Double.NaN;
        this.isPersistent = false;
        this.attributeDataType = AttributeDataType.VOID;
    }

    public AnalyticsAttribute(String name, String stringValue) {
        this(name, stringValue, true);
    }

    public AnalyticsAttribute(String name, String stringValue, boolean isPersistent) {
        super();
        this.name = name;
        setStringValue(stringValue);
        this.isPersistent = isPersistent;
    }

    public AnalyticsAttribute(String name, double doubleValue) {
        this(name, doubleValue, true);
    }

    public AnalyticsAttribute(String name, double doubleValue, boolean isPersistent) {
        super();
        this.name = name;
        setDoubleValue(doubleValue);
        this.isPersistent = isPersistent;
    }

    public AnalyticsAttribute(String name, boolean boolValue) {
        this(name, boolValue, true);
    }

    public AnalyticsAttribute(String name, boolean boolValue, boolean isPersistent) {
        super();
        this.name = name;
        setBooleanValue(boolValue);
        this.isPersistent = isPersistent;
    }

    public AnalyticsAttribute(AnalyticsAttribute clone) {
        this.name = clone.name;
        this.doubleValue = clone.doubleValue;
        this.stringValue = clone.stringValue;
        this.isPersistent = clone.isPersistent;
        this.attributeDataType = clone.attributeDataType;
    }

    public String getName() {
        return name;
    }

    public boolean isStringAttribute() {
        return attributeDataType == AttributeDataType.STRING;
    }

    public boolean isDoubleAttribute() {
        return attributeDataType == AttributeDataType.DOUBLE;
    }

    public boolean isBooleanAttribute() {
        return attributeDataType == AttributeDataType.BOOLEAN;
    }

    public String getStringValue() {
        return (attributeDataType == AttributeDataType.STRING) ? stringValue : null;
    }

    public void setStringValue(String stringValue) {
        this.doubleValue = Double.NaN;
        this.stringValue = stringValue;
        this.attributeDataType = AttributeDataType.STRING;
    }

    public double getDoubleValue() {
        return (attributeDataType == AttributeDataType.DOUBLE) ? doubleValue : Double.NaN;
    }

    public void setDoubleValue(double doubleValue) {
        this.doubleValue = doubleValue;
        this.stringValue = null;
        this.attributeDataType = AttributeDataType.DOUBLE;
    }

    public boolean getBooleanValue() {
        return (attributeDataType == AttributeDataType.BOOLEAN) ? Boolean.valueOf(stringValue).booleanValue() : false;
    }

    public void setBooleanValue(boolean boolValue) {
        this.stringValue = Boolean.toString(boolValue);
        this.doubleValue = Double.NaN;
        this.attributeDataType = AttributeDataType.BOOLEAN;
    }

    public boolean isPersistent() {
        // Do not persist if attribute is in exclusion list
        return this.isPersistent && !validator.isExcludedAttributeName(this.name);
    }

    public void setPersistent(boolean isPersistent) {
        this.isPersistent = isPersistent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AnalyticsAttribute attribute = (AnalyticsAttribute) o;

        if (!name.equals(attribute.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder("AnalyticsAttribute{");

        stringBuilder.append("name='" + name + "'");

        switch (attributeDataType) {
            case VOID:
                break;
            case STRING:
                stringBuilder.append(",stringValue='" + stringValue + "'");
                break;
            case DOUBLE:
                stringBuilder.append(",doubleValue='" + doubleValue + "'");
                break;
            case BOOLEAN:
                stringBuilder.append(",booleanValue=" + Boolean.valueOf(stringValue).toString());
                break;
        }

        stringBuilder.append(",isPersistent=" + isPersistent);
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    public AttributeDataType getAttributeDataType() {
        return attributeDataType;
    }

    public String valueAsString() {
        String value;

        switch (attributeDataType) {
            case STRING:
                value = stringValue;
                break;

            case DOUBLE:
                value = Double.toString(doubleValue);
                break;

            case BOOLEAN:
                value = Boolean.valueOf(getBooleanValue()).toString();
                break;

            default:
                value = null;
                break;
        }

        return value;
    }

    public JsonElement asJsonElement() {
        JsonPrimitive jsonPrimitive;

        switch (attributeDataType) {
            case STRING:
                jsonPrimitive = SafeJsonPrimitive.factory(getStringValue());
                break;

            case DOUBLE:
                jsonPrimitive = SafeJsonPrimitive.factory(getDoubleValue());
                break;

            case BOOLEAN:
                jsonPrimitive = SafeJsonPrimitive.factory(getBooleanValue());
                break;

            default:
                jsonPrimitive = null;
                break;
        }

        return jsonPrimitive;
    }

    public static Set<AnalyticsAttribute> newFromJson(JsonObject attributesJson) {
        final Set<AnalyticsAttribute> attributeSet = new HashSet<AnalyticsAttribute>();
        final Iterator<Map.Entry<String, JsonElement>> entry = attributesJson.entrySet().iterator();

        while (entry.hasNext()) {
            Map.Entry<String, JsonElement> elem = entry.next();
            String key = elem.getKey();
            if (elem.getValue().isJsonPrimitive()) {
                JsonPrimitive value = elem.getValue().getAsJsonPrimitive();
                if (value.isString()) {
                    attributeSet.add(new AnalyticsAttribute(key, value.getAsString(), false));
                } else if (value.isBoolean()) {
                    attributeSet.add(new AnalyticsAttribute(key, value.getAsBoolean(), false));
                } else if (value.isNumber()) {
                    attributeSet.add(new AnalyticsAttribute(key, value.getAsDouble(), false));
                }
            } else {
                attributeSet.add(new AnalyticsAttribute(key, elem.getValue().getAsString(), false));
            }
        }

        return attributeSet;
    }

    /**
     * Properties are not validated on construction
     */
    boolean isValid() {
        return validator.isValidAttribute(this);
    }

    /**
     * Create a new instance of AnalyticsAttribute seeded with passed key and value
     *
     * @param key   Valid name of attribute (enforced)
     * @param value One of supported types (String or Numeric)
     * @return Validated attribute, or null on error
     */
    static AnalyticsAttribute createAttribute(String key, Object value) {
        try {
            if (validator.isValidAttributeName(key)) {
                if (value instanceof String) {
                    if (validator.isValidAttributeValue(key, (String) value)) {
                        return new AnalyticsAttribute(key, String.valueOf(value));
                    }
                } else if (value instanceof Float) {
                    return new AnalyticsAttribute(key, (Float) value);
                } else if (value instanceof Double) {
                    return new AnalyticsAttribute(key, (Double) value);
                } else if (value instanceof Integer) {
                    return new AnalyticsAttribute(key, Double.valueOf((Integer) value));
                } else if (value instanceof Short) {
                    return new AnalyticsAttribute(key, Double.valueOf((Short) value));
                } else if (value instanceof Long) {
                    return new AnalyticsAttribute(key, Double.valueOf((Long) value));
                } else if (value instanceof BigDecimal) {
                    return new AnalyticsAttribute(key, ((BigDecimal) value).doubleValue());
                } else if (value instanceof BigInteger) {
                    return new AnalyticsAttribute(key, ((BigInteger) value).doubleValue());
                } else if (value instanceof Boolean) {
                    return new AnalyticsAttribute(key, (Boolean) value);
                } else {
                    log.error("Unsupported event attribute type for key [" + key + "]: " + value.getClass().getName());
                }
            }
        } catch (ClassCastException e) {
            log.error(String.format("Error casting attribute [%s] to String or Float: ", key), e);
        }

        return null;
    }

}
