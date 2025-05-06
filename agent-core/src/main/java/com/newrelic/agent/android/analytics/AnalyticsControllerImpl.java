/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.AgentImpl;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.api.v1.Defaults;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.EnvironmentInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.TraceLifecycleAware;
import com.newrelic.agent.android.tracing.TraceMachine;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnalyticsControllerImpl extends HarvestAdapter implements AnalyticsController {
    // Insights allows 254 attributes per event: the delta is allocated to events we create
    protected static final int MAX_ATTRIBUTES = 128;

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final AnalyticsControllerImpl instance = new AnalyticsControllerImpl();
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AnalyticsValidator validator = new AnalyticsValidator();

    private final ConcurrentLinkedQueue<AnalyticsAttribute> systemAttributes;
    private final ConcurrentLinkedQueue<AnalyticsAttribute> userAttributes;
    private final EventManagerImpl eventManager;
    private final AtomicBoolean isEnabled;
    private final InteractionCompleteListener interactionListener;

    private AgentImpl agentImpl;
    private AnalyticsAttributeStore attributeStore;

    private AnalyticsEventStore eventStore;

    class InteractionCompleteListener implements TraceLifecycleAware {
        @Override
        public void onEnterMethod() {
            // noop
        }

        @Override
        public void onExitMethod() {
            // noop
        }

        @Override
        public void onTraceStart(ActivityTrace activityTrace) {
            AnalyticsAttribute lastInteraction = new AnalyticsAttribute(AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE, activityTrace.getActivityName());
            addAttributeUnchecked(lastInteraction, true);
        }

        @Override
        public void onTraceComplete(ActivityTrace activityTrace) {
            log.audit("AnalyticsControllerImpl.InteractionCompleteListener.onTraceComplete()");

            // Fire off an interaction event
            AnalyticsEvent event = createTraceEvent(activityTrace);
            AnalyticsControllerImpl.getInstance().addEvent(event);
        }

        @Override
        public void onTraceRename(ActivityTrace activityTrace) {
            AnalyticsAttribute lastInteraction = new AnalyticsAttribute(AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE, activityTrace.getActivityName());
            addAttributeUnchecked(lastInteraction, true);
        }

        private AnalyticsEvent createTraceEvent(ActivityTrace activityTrace) {
            float durationInSec = activityTrace.rootTrace.getDurationAsSeconds();
            Set<AnalyticsAttribute> attrs = new HashSet<AnalyticsAttribute>();
            attrs.add(new AnalyticsAttribute(AnalyticsAttribute.INTERACTION_DURATION_ATTRIBUTE, durationInSec));

            // Internal event and attribute call must use the factory to bypass attribute validation
            return AnalyticsEventFactory.createEvent(activityTrace.rootTrace.displayName, AnalyticsEventCategory.Interaction, AnalyticsEvent.EVENT_TYPE_MOBILE, attrs);
        }
    }

    public static void initialize(AgentConfiguration agentConfiguration, AgentImpl agentImpl) {
        // needs a good impl and config
        if (agentConfiguration == null || agentImpl == null) {
            log.error("AnalyticsControllerImpl.initialize(): Can't initialize with a null agent configuration or implementation.");
            return;
        }

        log.audit("AnalyticsControllerImpl.initialize(" + agentConfiguration + ", " + agentImpl.toString() + ")");

        // Only initialize once.
        if (!initialized.compareAndSet(false, true)) {
            log.warn("AnalyticsControllerImpl.initialize(): Has already been initialized. Bypassing..");
            return;
        }

        instance.clear();
        instance.reinitialize(agentConfiguration, agentImpl);

        TraceMachine.addTraceListener(instance.interactionListener);
        Harvest.addHarvestListener(instance);

        log.info("Analytics Controller initialized: enabled[" + instance.isEnabled + "]");
    }

    public static void shutdown() {
        TraceMachine.removeTraceListener(instance.interactionListener);
        Harvest.removeHarvestListener(instance);
        instance.getEventManager().shutdown();
        initialized.compareAndSet(true, false);
        log.info("Analytics Controller shutdown");
    }

    private AnalyticsControllerImpl() {
        isEnabled = new AtomicBoolean(false);
        eventManager = new EventManagerImpl();
        systemAttributes = new ConcurrentLinkedQueue<AnalyticsAttribute>();
        userAttributes = new ConcurrentLinkedQueue<AnalyticsAttribute>();
        interactionListener = new InteractionCompleteListener();
    }

    void reinitialize(AgentConfiguration agentConfiguration, AgentImpl agentImpl) {
        log.audit("AnalyticsControllerImpl.reinitialize(" + agentConfiguration + ", " + agentImpl.toString() + ")");

        this.agentImpl = agentImpl;
        this.eventManager.initialize(agentConfiguration);
        this.isEnabled.set(agentConfiguration.getEnableAnalyticsEvents());
        this.attributeStore = agentConfiguration.getAnalyticsAttributeStore();
        this.eventStore = agentConfiguration.getEventStore();

        loadPersistentAttributes();

        // These systemAttributes are added by the agent to all attribute sets, so define them at
        // construction time to ensure they exist:
        //        uuid (unique device install id)
        //        osName
        //        osVersion
        //        osMajorVersion
        //        deviceManufacturer
        //        deviceModel
        //        carrier
        //        newRelicVersion (agent version #)
        //        memUsageMb
        //        sessionId (unique guid generated per session)

        DeviceInformation deviceInformation = this.agentImpl.getDeviceInformation();
        String osVersion = deviceInformation.getOsVersion();

        if (osVersion != null) {
            osVersion = osVersion.replace(" ", "");     // squash whitespace
            if (!osVersion.isEmpty()) {
                String osMajorVersion = null;

                // split at any of these delimiters
                String[] osMajorVersionArr = osVersion.split("[.:-]");
                if (osMajorVersionArr.length > 0) {
                    osMajorVersion = osMajorVersionArr[0];
                }

                if (osMajorVersion == null || osMajorVersion.isEmpty()) {
                    osMajorVersion = osVersion;
                }

                systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE, osVersion));
                systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE, osMajorVersion));
            }
        }

        if (osVersion == null || osVersion.isEmpty()) {
            systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE, "undefined"));
        }

        EnvironmentInformation environmentInformation = this.agentImpl.getEnvironmentInformation();

        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.OS_NAME_ATTRIBUTE, deviceInformation.getOsName()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.OS_BUILD_ATTRIBUTE, deviceInformation.getOsBuild()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.DEVICE_MANUFACTURER_ATTRIBUTE, deviceInformation.getManufacturer()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.DEVICE_MODEL_ATTRIBUTE, deviceInformation.getModel()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.UUID_ATTRIBUTE, deviceInformation.getDeviceId()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.CARRIER_ATTRIBUTE, agentImpl.getNetworkCarrier()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.NEW_RELIC_VERSION_ATTRIBUTE, deviceInformation.getAgentVersion()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.MEM_USAGE_MB_ATTRIBUTE, (float) environmentInformation.getMemoryUsage()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE, agentConfiguration.getSessionID(), false));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.PROCESS_ID_ATTRIBUTE,agentImpl.getCurrentProcessId(), false));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, agentConfiguration.getApplicationFramework().toString()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.APPLICATION_PLATFORM_VERSION_ATTRIBUTE, agentConfiguration.getApplicationFrameworkVersion()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.RUNTIME_ATTRIBUTE, deviceInformation.getRunTime()));
        systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.ARCHITECTURE_ATTRIBUTE, deviceInformation.getArchitecture()));

        if (agentConfiguration.getCustomBuildIdentifier() != null) {
            systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.APP_BUILD_ATTRIBUTE, agentConfiguration.getCustomBuildIdentifier()));
        } else {
            String appBuildString = String.valueOf(Agent.getApplicationInformation().getVersionCode());
            if (!appBuildString.isEmpty()) {
                systemAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.APP_BUILD_ATTRIBUTE, appBuildString));
            }
        }
    }

    /**
     * global attribute by name
     *
     * @return Named attribute, whether created or updated
     */
    @Override
    public AnalyticsAttribute getAttribute(String name) {
        log.audit("AnalyticsControllerImpl.getAttribute(" + name + ")");

        AnalyticsAttribute attribute = getUserAttribute(name);
        if (attribute == null) {
            attribute = getSystemAttribute(name);
        }
        return attribute;
    }


    /**
     * API: Get the system attribute collection
     *
     * @return Immutable, unbounded set of system attributes
     */
    @Override
    public Set<AnalyticsAttribute> getSystemAttributes() {
        log.audit("AnalyticsControllerImpl.getSystemAttributes(): " + systemAttributes.size());

        // The set of returned system attributes, and each attribute within it, should be immutable
        final Set<AnalyticsAttribute> attrs = new HashSet<AnalyticsAttribute>(systemAttributes.size());

        for (AnalyticsAttribute attr : systemAttributes) {
            // Clone the attribute to prevent it from being modified after addition to the set
            attrs.add(new AnalyticsAttribute(attr));
        }

        return Collections.unmodifiableSet(attrs);
    }

    /**
     * API: Get the user attribute collection
     *
     * @return Immutable set of user attributes, bounded to attribute limit
     */
    @Override
    public Set<AnalyticsAttribute> getUserAttributes() {
        log.audit("AnalyticsControllerImpl.getUserAttributes(): " + userAttributes.size());

        // The set of returned user attributes, and each attribute within it, should be immutable
        final Set<AnalyticsAttribute> attrs = new HashSet<AnalyticsAttribute>(userAttributes.size());

        for (AnalyticsAttribute attr : userAttributes) {
            // Clone the attribute to prevent it from being modified after addition to the set
            attrs.add(new AnalyticsAttribute(attr));

            // only return the first 128 attributes
            if (attrs.size() == MAX_ATTRIBUTES) {
                break;
            }
        }

        return Collections.unmodifiableSet(attrs);
    }

    /**
     * API: Get the global attribute collection
     *
     * @return Immutable union of system and user attributes
     */
    @Override
    public Set<AnalyticsAttribute> getSessionAttributes() {
        log.audit("AnalyticsControllerImpl.getSessionAttributes(): " + getSessionAttributeCount());

        // The set of returned session attributes, and each attribute within it, should be immutable
        final Set<AnalyticsAttribute> attrs = new HashSet<AnalyticsAttribute>(getSessionAttributeCount());

        attrs.addAll(getSystemAttributes());
        attrs.addAll(getUserAttributes());

        return Collections.unmodifiableSet(attrs);
    }

    @Override
    public int getSystemAttributeCount() {
        return systemAttributes.size();
    }

    @Override
    public int getUserAttributeCount() {
        return Math.min(userAttributes.size(), MAX_ATTRIBUTES);
    }

    @Override
    public int getSessionAttributeCount() {
        return systemAttributes.size() + userAttributes.size();
    }

    /**
     * API: Set *persisted* global attribute with specified name and String value
     *
     * @return true if attribute as created or updated
     */
    @Override
    public boolean setAttribute(String name, String value) {
        // All attributes are persisted by default.
        return setAttribute(name, value, true);
    }

    /**
     * API: Set global attribute with specified name and boolean value, optionally persisted
     *
     * @return true if attribute as created or updated
     */
    @Override
    public boolean setAttribute(String name, String value, boolean persistent) {
        log.audit("AnalyticsControllerImpl.setAttribute(" + name + ", " + value + ")" + (persistent ? "(persistent)" : "(transient)"));

        if (!isInitializedAndEnabled()) {
            return false;
        }

        if (!validator.isValidAttributeName(name) || !validator.isValidAttributeValue(name, value)) {
            return false;
        }

        AnalyticsAttribute cachedAttribute = getAttribute(name);

        if (cachedAttribute == null) {
            // This is a new attribute
            return addNewUserAttribute(new AnalyticsAttribute(name, value, persistent));
        } else {
            cachedAttribute.setStringValue(value);
            cachedAttribute.setPersistent(persistent);
            if (cachedAttribute.isPersistent()) {
                if (!attributeStore.store(cachedAttribute)) {
                    log.error("Failed to store attribute [" + cachedAttribute + "] to attribute store.");
                    return false;
                }
            } else {
                attributeStore.delete(cachedAttribute);
            }
        }

        return true;
    }

    /**
     * API: Set *persisted* global attribute with specified name and Double value
     *
     * @return true if attribute as created or updated
     */
    @Override
    public boolean setAttribute(String name, double value) {
        // All attributes are persisted by default.
        return setAttribute(name, value, true);
    }

    /**
     * API: Set global attribute with specified name and Double value, optionally persisted
     *
     * @return true if attribute as created or updated
     */
    @Override
    public boolean setAttribute(String name, double value, boolean persistent) {
        log.audit("AnalyticsControllerImpl.setAttribute(" + name + ", " + value + ")" + (persistent ? " (persistent)" : " (transient)"));

        if (!isInitializedAndEnabled()) {
            return false;
        }

        if (!validator.isValidAttributeName(name)) {
            return false;
        }

        AnalyticsAttribute cachedAttribute = getAttribute(name);

        if (cachedAttribute == null) {
            // This is a new attribute
            return addNewUserAttribute(new AnalyticsAttribute(name, value, persistent));
        } else {
            cachedAttribute.setDoubleValue(value);
            cachedAttribute.setPersistent(persistent);
            if (cachedAttribute.isPersistent()) {
                if (!attributeStore.store(cachedAttribute)) {
                    log.error("Failed to store attribute [" + cachedAttribute + "] to attribute store.");
                    return false;
                }
            } else {
                attributeStore.delete(cachedAttribute);
            }
        }
        return true;
    }

    /**
     * API: Set *persisted* global attribute with specified name and boolean value
     *
     * @return true if attribute as created or updated
     */
    @Override
    public boolean setAttribute(String name, boolean value) {
        // All attributes are persisted by default.
        return setAttribute(name, value, true);
    }

    /**
     * API: Set global attribute with specified name and boolean value, optionally persisted
     *
     * @return true if attribute as created or updated
     */
    @Override
    public boolean setAttribute(String name, boolean value, boolean persistent) {
        log.audit("AnalyticsControllerImpl.setAttribute(" + name + ", " + value + ")" + (persistent ? " (persistent)" : " (transient)"));

        if (!isInitializedAndEnabled()) {
            return false;
        }

        if (!validator.isValidAttributeName(name)) {
            return false;
        }

        AnalyticsAttribute cachedAttribute = getAttribute(name);
        if (cachedAttribute == null) {
            // This is a new attribute
            return addNewUserAttribute(new AnalyticsAttribute(name, value, persistent));
        } else {
            cachedAttribute.setBooleanValue(value);
            cachedAttribute.setPersistent(persistent);
            if (cachedAttribute.isPersistent()) {
                if (!attributeStore.store(cachedAttribute)) {
                    log.error("Failed to store attribute [" + cachedAttribute + "] to attribute store.");
                    return false;
                }
            } else {
                attributeStore.delete(cachedAttribute);
            }
        }

        return true;
    }

    /**
     * Add/update an attribute in the controller, ignoring reserved name check
     * and attribute size limits. Intended for internal use only.
     *
     * @param attribute
     * @param persistent
     * @return true if added to global attributes
     */
    public boolean addAttributeUnchecked(AnalyticsAttribute attribute, boolean persistent) {
        log.audit("AnalyticsControllerImpl.setAttributeUnchecked(" + attribute.getName() + ")" +
                attribute.getStringValue() + (persistent ? " (persistent)" : " (transient)"));

        if (!initialized.get()) {
            log.warn("Analytics controller is not initialized!");
            return false;
        }

        if (!isEnabled.get()) {
            log.warn("Analytics controller is not enabled!");
            return false;
        }

        final String name = attribute.getName();

        if (!validator.isValidKeyName(name)) {
            return false;
        }

        if (attribute.isStringAttribute()) {
            if (!validator.isValidAttributeValue(name, attribute.getStringValue())) {
                return false;
            }
        }

        final AnalyticsAttribute cachedAttribute = getSystemAttribute(name);

        if (cachedAttribute == null) {
            // This is a new attribute
            // Add the new attribute to the set
            systemAttributes.add(attribute);
            if (attribute.isPersistent()) {
                if (!attributeStore.store(attribute)) {
                    log.error("Failed to store attribute " + attribute + " to attribute store.");
                    return false;
                }
            }
        } else {
            switch (attribute.getAttributeDataType()) {
                case STRING:
                    cachedAttribute.setStringValue(attribute.getStringValue());
                    break;
                case DOUBLE:
                    cachedAttribute.setDoubleValue(attribute.getDoubleValue());
                    break;
                case BOOLEAN:
                    cachedAttribute.setBooleanValue(attribute.getBooleanValue());
                    break;
                default:
                    log.error("Attribute data type [" + attribute.getAttributeDataType() + "] is invalid");
                    break;
            }

            cachedAttribute.setPersistent(persistent);

            if (cachedAttribute.isPersistent()) {
                if (!attributeStore.store(cachedAttribute)) {
                    log.error("Failed to store attribute [" + cachedAttribute + "] to attribute store.");
                    return false;
                }
            } else {
                attributeStore.delete(cachedAttribute);
            }
        }

        return true;
    }

    @Override
    public boolean incrementAttribute(String name, double value) {
        // All attributes are persisted by default.
        return incrementAttribute(name, value, true);
    }

    @Override
    public boolean incrementAttribute(String name, double value, boolean persistent) {
        log.audit("AnalyticsControllerImpl.incrementAttribute(" + name + ", " + value + ") " + (persistent ? " (persistent)" : " (transient)"));

        if (!isInitializedAndEnabled()) {
            return false;
        }

        if (!validator.isValidAttributeName(name)) {
            return false;
        }

        AnalyticsAttribute cachedAttribute = getAttribute(name);

        if (cachedAttribute != null && cachedAttribute.isDoubleAttribute()) {
            // The attribute with the provided name was previously a Double.  Get the value and increment it
            cachedAttribute.setDoubleValue(cachedAttribute.getDoubleValue() + value);
            cachedAttribute.setPersistent(persistent);

            if (cachedAttribute.isPersistent()) {
                if (!attributeStore.store(cachedAttribute)) {
                    log.error("Failed to store attribute " + cachedAttribute + " to attribute store.");
                    return false;
                }
            }
        } else {
            // The attribute is not currently defined, String or boolean
            if (cachedAttribute == null) {
                return addNewUserAttribute(new AnalyticsAttribute(name, value, persistent));
            } else {
                // The attribute is defined, and is currently a String.  Log a warning and return
                // false to indicate the operation cannot be performed.
                log.warn("Cannot increment attribute " + name + ": the attribute is already defined as a non-float value.");
                return false;
            }
        }

        return true;
    }

    /**
     * Remove specified global attribute from controller and attribute store
     *
     * @return true if attribute was removed
     */
    @Override
    public boolean removeAttribute(String name) {
        log.audit("AnalyticsControllerImpl.removeAttribute(" + name + ")");

        if (!isInitializedAndEnabled()) {
            return false;
        }

        AnalyticsAttribute cachedAttribute = getAttribute(name);
        if (cachedAttribute != null) {
            userAttributes.remove(cachedAttribute);
            if (cachedAttribute.isPersistent()) {
                attributeStore.delete(cachedAttribute);
            }
        }

        return true;
    }

    /**
     * Remove all global attributes from controller and attribute store
     *
     * @return true if attributes were removed
     */
    @Override
    public boolean removeAllAttributes() {
        if (attributeStore != null && userAttributes != null) {
            log.audit("AnalyticsControllerImpl.removeAttributes(): " + attributeStore.count() + userAttributes.size());


            if (!isInitializedAndEnabled()) {
                return false;
            }

            attributeStore.clear();
            userAttributes.clear();

            return true;
        } else{
            return false;
        }
    }


    //
    // Event related methods (could be refactored out)
    //

    /**
     * API: Create validated AnalyticsEvent, drawing name, category and type from passed attributes.
     *
     * @param name            Valid event name
     * @param eventAttributes Set of event attribute k/v pairs
     * @return true if added to pool
     */
    @Override
    public boolean addEvent(String name, Set<AnalyticsAttribute> eventAttributes) {
        return addEvent(name, AnalyticsEventCategory.Custom, AnalyticsEvent.EVENT_TYPE_MOBILE, eventAttributes);
    }

    /**
     * Create a pre-validated AnalyticsEvent from specified name, category and type, and add to the event pool.
     *
     * @param name            Valid event name
     * @param eventCategory   Valid event category type
     * @param eventType       Valid event type
     * @param eventAttributes Set of event attribute k/v pairs
     * @return true if added to pool
     */
    @Override
    public boolean addEvent(String name, AnalyticsEventCategory eventCategory, String eventType, Set<AnalyticsAttribute> eventAttributes) {

        if (!isInitializedAndEnabled()) {
            return false;
        }

        AnalyticsEvent event = AnalyticsEventFactory.createEvent(name, eventCategory, eventType, eventAttributes);

        return addEvent(event);
    }

    /**
     * API: Add a validated AnalyticsEvent to the event pool.
     *
     * @param event Validated event
     * @return true if added to pool
     */
    @Override
    public boolean addEvent(AnalyticsEvent event) {
        log.audit("AnalyticsControllerImpl.addEvent(" + (event.getName() == null ? event.getEventType() : event.getName()) + ")");

        if (!isInitializedAndEnabled()) {
            return false;
        }

        final Set<AnalyticsAttribute> sessionAttributes = new HashSet<AnalyticsAttribute>();

        // Make sure the Harvest instance is valid
        long sessionDuration = agentImpl.getSessionDurationMillis();
        if (Harvest.INVALID_SESSION_DURATION == sessionDuration) {
            log.error("Harvest instance is not running! Session duration will be invalid");
        } else {
            sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE, sessionDuration / 1000.00f));
            event.addAttributes(sessionAttributes);
        }

        return eventManager.addEvent(event);
    }

    @Override
    public int getMaxEventPoolSize() {
        return eventManager.getMaxEventPoolSize();
    }

    @Override
    public void setMaxEventPoolSize(int maxSize) {
        eventManager.setMaxEventPoolSize(maxSize);
    }

    @Override
    public void setMaxEventBufferTime(int maxBufferTimeInSec) {
        eventManager.setMaxEventBufferTime(maxBufferTimeInSec);
    }

    @Override
    public int getMaxEventBufferTime() {
        return eventManager.getMaxEventBufferTime();
    }

    @Override
    public EventManager getEventManager() {
        return eventManager;
    }

    // Static singleton accessor
    public static AnalyticsControllerImpl getInstance() {
        return instance;
    }

    // Internal/utility functions
    void loadPersistentAttributes() {
        log.audit("AnalyticsControllerImpl.loadPersistentAttributes(): " + attributeStore.count());

        // Load any persistent attributes from the AttributeStore
        List<AnalyticsAttribute> storedAttrs = attributeStore.fetchAll();
        log.debug("AnalyticsControllerImpl.loadPersistentAttributes(): found " + storedAttrs.size() + " userAttributes in the attribute store");
        int size = userAttributes.size();
        for (AnalyticsAttribute attr : storedAttrs) {
            if (!userAttributes.contains(attr) && (size <= MAX_ATTRIBUTES)) {
                userAttributes.add(attr);
                size++;
            }
        }
    }

    private AnalyticsAttribute getSystemAttribute(String name) {
        AnalyticsAttribute attribute = null;

        for (AnalyticsAttribute nextAttribute : systemAttributes) {
            if (nextAttribute.getName().equals(name)) {
                attribute = nextAttribute;
                break;
            }
        }

        return attribute;
    }

    private AnalyticsAttribute getUserAttribute(String name) {
        AnalyticsAttribute attribute = null;

        for (AnalyticsAttribute nextAttribute : userAttributes) {
            if (nextAttribute.getName().equals(name)) {
                attribute = nextAttribute;
                break;
            }
        }

        return attribute;
    }

    public void clear() {
        log.audit("AnalyticsControllerImpl.clear(): system[" + systemAttributes.size() +
                "] user[" + userAttributes.size() + "] events[" + eventManager.size() + "]");

        systemAttributes.clear();
        userAttributes.clear();
        eventManager.empty();
    }

    /**
     * API: Record a custom of specified type and name
     *
     * @param eventType       standalone type for this event. E.g Mobile, MobileRequestError, MobileRequest.
     * @param eventAttributes map of attributes to attach to this event.
     * @return true if successfully recorded the custom event.
     */
    @Override
    public boolean recordCustomEvent(String eventType, Map<String, Object> eventAttributes) {
        try {
            log.audit("AnalyticsControllerImpl.recordCustomEvent(" + eventType + ", " + eventAttributes + ")");

            if (!isInitializedAndEnabled()) {
                return false;
            }

            if (!validator.isValidEventType(eventType) || validator.isReservedEventType(eventType)) {
                return false;
            }

            String eventName = eventType;
            final Set<AnalyticsAttribute> attributes = new HashSet<>();

            attributes.addAll(validator.toValidatedAnalyticsAttributes(eventAttributes));
            String overrideName = (String) eventAttributes.get(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE);
            if (!(overrideName == null || overrideName.isEmpty())) {
                eventName = overrideName;
            }

            return addEvent(eventName, AnalyticsEventCategory.Custom, eventType, attributes);

        } catch (Exception e) {
            log.error(String.format("Error occurred while recording custom event [%s]: ", eventType), e);
        }

        return false;
    }

    /**
     * API: Record MobileBreadcrumb from passed name and attributes
     *
     * @param name
     * @param eventAttributes
     * @return True if added to event pool
     */
    public boolean recordBreadcrumb(String name, Map<String, Object> eventAttributes) {
        try {
            log.audit("AnalyticsControllerImpl.recordBreadcrumb(" + name + ", " + eventAttributes + ")");

            if (!isInitializedAndEnabled()) {
                return false;
            }

            Set<AnalyticsAttribute> attributes = new HashSet<>();
            attributes.addAll(validator.toValidatedAnalyticsAttributes(eventAttributes));

            return addEvent(name, AnalyticsEventCategory.Breadcrumb, AnalyticsEvent.EVENT_TYPE_MOBILE_BREADCRUMB, attributes);
        } catch (Exception e) {
            log.error(String.format("Error occurred while recording Breadcrumb event [%s]: ", name), e);
        }

        return false;
    }

    /**
     * Records an event without validating attributes! Only for internal event use!
     *
     * @param name            name attribute. can be the same as eventType.
     * @param eventCategory   category for this particular event. Will be available as an attribute.
     * @param eventType       standalone type for this event. E.g Mobile, MobileRequestError, MobileRequest.
     * @param eventAttributes map of attributes to attach to this event.
     * @return true if successfully recorded the event.
     */
    public boolean internalRecordEvent(String name, AnalyticsEventCategory eventCategory, String eventType, Map<String, Object> eventAttributes) {
        try {
            log.audit("AnalyticsControllerImpl.internalRecordEvent(" + name + ", " + eventCategory.toString() + ", " + eventType + ", " + eventAttributes + ")");

            if (!isInitializedAndEnabled()) {
                return false;
            }

            if (!validator.isValidEventType(eventType)) {
                return false;
            }

            Set<AnalyticsAttribute> attributes = validator.toValidatedAnalyticsAttributes(eventAttributes);

            return addEvent(name, eventCategory, eventType, attributes);

        } catch (Exception e) {
            log.error(String.format("Error occurred while recording event [%s]: ", name), e);
        }

        return false;
    }

    /**
     * API: Record a custom event using specified name, type and category contained
     * in passed attributes.
     *
     * @param name            name attribute. can be the same as eventType.
     * @param eventAttributes map of attributes to attach to this event.
     * @return true if successfully recorded the event.
     */
    @Override
    public boolean recordEvent(String name, Map<String, Object> eventAttributes) {
        try {
            log.audit("AnalyticsControllerImpl.recordEvent - " + name + ": " + eventAttributes.size() + " attributes");

            if (!isInitializedAndEnabled()) {
                return false;
            }

            final Set<AnalyticsAttribute> attributes = new HashSet<AnalyticsAttribute>();
            attributes.addAll(validator.toValidatedAnalyticsAttributes(eventAttributes));

            return addEvent(name, AnalyticsEventCategory.Custom, AnalyticsEvent.EVENT_TYPE_MOBILE, attributes);

        } catch (Exception e) {
            log.error(String.format("Error occurred while recording event [%s]: ", name), e);
        }

        return false;
    }

    /**
     * Record an NetworkRequestError event using specified transaction.
     *
     * @param txn HttpTransaction from which to gather event attributes
     * @return true if successfully recorded the event.
     */
    void createHttpErrorEvent(HttpTransaction txn) {
        if (isInitializedAndEnabled()) {
            NetworkEventController.createHttpErrorEvent(txn);
        }
    }

    /**
     * Record an NetworkRequestError event using specified transaction.
     *
     * @param txn HttpTransaction from which to gather event attributes
     * @return true if successfully recorded the event.
     */
    void createNetworkFailureEvent(HttpTransaction txn) {
        if (isInitializedAndEnabled()) {
            NetworkEventController.createNetworkFailureEvent(txn);
        }
    }

    /**
     * Record an NetworkRequest event using specified transaction.
     *
     * @param txn HttpTransaction from which to gather event attributes
     * @return true if successfully recorded the event.
     */
    void createNetworkRequestEvent(HttpTransaction txn) {
        if (isInitializedAndEnabled()) {
            NetworkEventController.createNetworkRequestEvent(txn);
        }
    }

    /**
     * Record a NetworkRequest events using specified transaction.
     *
     * @param txn HttpTransaction from which to gather event attributes
     * @return true if successfully recorded the event.
     */
    public void createNetworkRequestEvents(HttpTransaction txn) {
        if (isInitializedAndEnabled()) {
            if (isHttpError(txn)) {
                NetworkEventController.createHttpErrorEvent(txn);
            } else if (isNetworkFailure(txn)) {
                NetworkEventController.createNetworkFailureEvent(txn);
            } else if (isSuccessfulRequest(txn)) {
                NetworkEventController.createNetworkRequestEvent(txn);
            }
        }
    }

    private boolean isNetworkFailure(HttpTransaction txn) {
        return txn.getErrorCode() != 0;
    }

    private boolean isHttpError(HttpTransaction txn) {
        return txn.getStatusCode() >= Defaults.MIN_HTTP_ERROR_STATUS_CODE;
    }

    private boolean isSuccessfulRequest(HttpTransaction txn) {
        return txn.getStatusCode() > 0 && txn.getStatusCode() < 400;
    }

    private boolean isInitializedAndEnabled() {
        if (!initialized.get()) {
            log.warn("Analytics controller is not initialized!");
            return false;
        }

        if (!isEnabled.get()) {
            log.warn("Analytics controller is not enabled!");
            return false;
        }

        return true;
    }

    public void setEnabled(boolean enabled) {
        isEnabled.set(enabled);
    }

    private boolean addNewUserAttribute(final AnalyticsAttribute attribute) {
        // This is a new attribute
        if (userAttributes.size() < MAX_ATTRIBUTES) {
            if (validator.isValidAttribute(attribute)) {
                // Add the new attribute to the set
                userAttributes.add(attribute);
                if (attribute.isPersistent()) {
                    if (!attributeStore.store(attribute)) {
                        log.error("Failed to store attribute [" + attribute + "] to attribute store.");
                        return false;
                    }
                }
            } else {
                log.error("Refused to add invalid attribute: " + attribute.getName());
            }

        } else {
            // The user-defined attribute limit has been reached
            log.warn("Attribute limit exceeded: " + MAX_ATTRIBUTES + " are allowed.");
            if (log.getLevel() >= AgentLog.AUDIT) {
                log.audit("Currently defined attributes:");
                for (AnalyticsAttribute attr : userAttributes) {
                    log.audit("\t" + attr.getName() + ": " + attr.valueAsString());
                }
            }
        }

        return true;
    }

    @Override
    public void onHarvest() {
        // Preparing to transmit the harvest data
        HarvestData harvestData = Harvest.getInstance().getHarvestData();
        if (harvestData != null) {
            harvestData.setAnalyticsEnabled(isEnabled.get());
            if (isEnabled.get() && FeatureFlag.featureEnabled(FeatureFlag.AnalyticsEvents)) {
                // Next, check to see if the event queue needs to be transmitted
                if (eventManager.isTransmitRequired()) {
                    // Populate the session attributes into the harvest data now, to ensure they
                    // have the most up-to-date values. Collect the session attributes.
                    // The session attributes are only included in the harvest data if there are
                    // events that must be transmitted.
                    Set<AnalyticsAttribute> sessionAttributes = new HashSet<AnalyticsAttribute>();
                    sessionAttributes.addAll(getSystemAttributes());
                    sessionAttributes.addAll(getUserAttributes());
                    harvestData.setSessionAttributes(sessionAttributes);

                    // hand-off current event set atomically
                    Collection<AnalyticsEvent> pendingEvents = eventManager.getQueuedEventsSnapshot();
                    if (pendingEvents.size() > 0) {
                        harvestData.getAnalyticsEvents().addAll(pendingEvents);
                        log.debug("EventManager: [" + pendingEvents.size() + "] events moved from buffer to HarvestData");

                        //remove events from pref
                        if (eventStore != null) {
                            for (AnalyticsEvent event : pendingEvents) {
                                eventStore.delete(event);
                            }
                        }
                    }

                    // event buffer _should_ be empty, but...
                    if (eventManager.getQueuedEvents().size() > 0) {
                        // yea it can happen that fast
                        log.error("EventManager: [" + eventManager.getQueuedEvents().size() + "] events remain in buffer after hand-off");
                    }
                }
            }
        }
    }
}
