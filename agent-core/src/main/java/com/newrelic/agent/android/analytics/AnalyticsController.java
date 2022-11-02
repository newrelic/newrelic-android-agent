/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Map;
import java.util.Set;

/**
 * Interface to the analytics event engine.
 */
public interface AnalyticsController {

    /**
     * Retrieves an attribute by name.
     *
     * @param name The attribute name.
     * @return The attribute requested, or null if none exists for the given name.
     */
    public AnalyticsAttribute getAttribute(String name);

    /**
     * Returns an immutable Set containing the automatically generated system attributes
     *
     * @return an immutable Set containing the automatically generated system attributes
     */
    public Set<AnalyticsAttribute> getSystemAttributes();

    /**
     * Returns an immutable Set containing the user-defined attributes
     *
     * @return an immutable Set containing the user-defined attributes
     */
    public Set<AnalyticsAttribute> getUserAttributes();

    /**
     * Returns an immutable Set containing the a union of
     * user-defined and system attributes
     *
     * @return an immutable Set containing the session attributes
     */
    public Set<AnalyticsAttribute> getSessionAttributes();

    /**
     * Returns the count of defined attributes.
     *
     * @return the count of defined attributes.
     */
    public int getSystemAttributeCount();

    /**
     * Returns the count of defined attributes.
     *
     * @return the count of defined attributes.
     */
    public int getUserAttributeCount();

    /**
     * Returns the count of defined attributes.
     *
     * @return the count of defined attributes.
     */
    public int getSessionAttributeCount();

    /**
     * Sets an attribute value.
     *
     * @param name       The attribute name
     * @param value      The attribute value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean setAttribute(String name, String value);

    /**
     * Sets an attribute value.
     *
     * @param name       The attribute name
     * @param value      The attribute value
     * @param persistent Whether the attribute value is persistent across sessions.
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean setAttribute(String name, String value, boolean persistent);

    /**
     * Sets an attribute value.
     *
     * @param name       The attribute name
     * @param value      The attribute value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean setAttribute(String name, double value);

    /**
     * Sets an attribute value.
     *
     * @param name       The attribute name
     * @param value      The attribute value
     * @param persistent Whether the attribute value is persistent across sessions.
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean setAttribute(String name, double value, boolean persistent);

    /**
     * Sets a boolean attribute value.
     *
     * @param name       The attribute name
     * @param value      The boolean attribute value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean setAttribute(String name, boolean value);

    /**
     * Sets a boolean attribute value.
     *
     * @param name       The attribute name
     * @param value      The boolean attribute value
     * @param persistent Whether the attribute value is persistent across sessions.
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean setAttribute(String name, boolean value, boolean persistent);


    /**
     * Increments the value of an attribute.
     *
     * @param name       The attribute name
     * @param value      Amount by which to increment the value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean incrementAttribute(String name, double value);

    /**
     * Increments the value of an attribute.
     *
     * @param name       The attribute name
     * @param value      Amount by which to increment the value
     * @param persistent Whether the attribute value is persistent across sessions.
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean incrementAttribute(String name, double value, boolean persistent);

    /**
     * Removes an attribute.
     *
     * @param name The attribute name
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean removeAttribute(String name);

    /**
     * Removes all attributes.
     *
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean removeAllAttributes();

    /**
     * Adds an event to the queue
     *
     * @param event
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    boolean addEvent(AnalyticsEvent event);

    /**
     * Adds an event to the queue
     *
     * @param name event name
     * @param eventAttributes custom attribute set for the event
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    boolean addEvent(String name, Set<AnalyticsAttribute> eventAttributes);

    /**
     * Adds an event to the queue
     *
     * @param name event name
     * @param eventCategory
     * @param eventType
     * @param eventAttributes custom attribute set for the event
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    boolean addEvent(String name, AnalyticsEventCategory eventCategory, String eventType, Set<AnalyticsAttribute> eventAttributes);

    /**
     * Returns the maximum pool size for the event queue.
     *
     * @return the maximum pool size for the event queue.
     */
    public int getMaxEventPoolSize();

    /**
     * Sets the maximum pool size for the event queue.
     *
     * @param maxSize the maximum pool size for the event queue.
     */
    public void setMaxEventPoolSize(int maxSize);

    /**
     * Returns the maximum buffer time an event in the queue.
     *
     * @return the maximum buffer time an event in the queue.
     */
    public int getMaxEventBufferTime();

    /**
     * Sets the maximum buffer time an event in the queue.
     *
     * @param maxBufferTimeInSec the maximum buffer time an event in the queue.
     */
    public void setMaxEventBufferTime(int maxBufferTimeInSec);

    /**
     * Returns the EventManager used by this controller to manage event lifecycle.
     *
     * @return the EventManager used by this controller to manage event lifecycle.
     */
    public EventManager getEventManager();

    /**
     * Record event with associated attributes
     *
     * @param name event name
     * @param eventAttributes custom attribute set for the event
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean recordEvent(String name, Map<String, Object> eventAttributes);

    /**
     * Record custom event with associated attributes
     *
     * @param eventType Custom event tyope
     * @param eventAttributes custom attribute set for the event
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public boolean recordCustomEvent(String eventType, Map<String, Object> eventAttributes);

}
