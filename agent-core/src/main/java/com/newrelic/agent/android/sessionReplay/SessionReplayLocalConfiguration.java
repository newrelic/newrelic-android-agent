package com.newrelic.agent.android.sessionReplay;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Local configuration class for session replay settings that can be modified at runtime.
 * This class manages client-side configuration that supplements the remote configuration
 * received from the server.
 */
public class SessionReplayLocalConfiguration {

    /**
     * Text masking strategy for session replay
     */
    private TextMaskingStrategy textMaskingStrategy;

    /**
     * Whether to mask all user touches during session replay
     */
    private boolean maskAllUserTouches;

    /**
     * Whether to mask application text during session replay
     */
    private boolean maskApplicationText;

    /**
     * Whether to mask user input text during session replay
     */
    private boolean maskUserInputText;

    /**
     * Set of view class names that should be masked during session replay
     */
    private Set<String> maskedViewClasses;

    /**
     * Set of view class names that should be explicitly unmasked during session replay
     */
    private Set<String> unmaskedViewClasses;

    /**
     * Set of view tags that should be masked during session replay
     */
    private Set<String> maskedViewTags;

    /**
     * Set of view tags that should be explicitly unmasked during session replay
     */
    private Set<String> unmaskedViewTags;

    /**
     * Default constructor with default values
     */
    public SessionReplayLocalConfiguration() {
        this.textMaskingStrategy = TextMaskingStrategy.MASK_ALL_TEXT;
        this.maskAllUserTouches = false;
        this.maskedViewClasses = new HashSet<>();
        this.unmaskedViewClasses = new HashSet<>();
        this.maskedViewTags = new HashSet<>();
        this.unmaskedViewTags = new HashSet<>();
        this.maskApplicationText = true; // Default to masking application text
        this.maskUserInputText = true; // Default to masking user input text
    }


    /**
     * Gets the current text masking strategy.
     *
     * @return The current text masking strategy
     */
    public TextMaskingStrategy getTextMaskingStrategy() {
        return textMaskingStrategy;
    }

    /**
     * Sets the text masking strategy to use for session replay.
     *
     * @param strategy The text masking strategy to apply
     */
    public void setTextMaskingStrategy(TextMaskingStrategy strategy) {
        this.textMaskingStrategy = strategy;
        if(this.textMaskingStrategy == TextMaskingStrategy.MASK_ALL_TEXT) {
            this.maskApplicationText = true;
            this.maskUserInputText = true;
        } else if (this.textMaskingStrategy == TextMaskingStrategy.MASK_USER_INPUT_TEXT) {
            this.maskApplicationText = false;
            this.maskUserInputText = true;
        } else {
            this.maskApplicationText = false;
            this.maskUserInputText = false;
        }
    }

    /**
     * Gets whether all user touches should be masked.
     *
     * @return true if user touches should be masked, false otherwise
     */
    public boolean isMaskAllUserTouches() {
        return maskAllUserTouches;
    }

    /**
     * Sets whether all user touches should be masked during session replay.
     *
     * @param maskAllUserTouches true to mask user touches, false otherwise
     */
    public void setMaskAllUserTouches(boolean maskAllUserTouches) {
        this.maskAllUserTouches = maskAllUserTouches;
    }

    /**
     * Gets the set of view class names that should be masked during session replay.
     *
     * @return The set of view class names to mask
     */
    public Set<String> getMaskedViewClasses() {
        if (maskedViewClasses == null) {
            maskedViewClasses = new HashSet<>();
        }
        return maskedViewClasses;
    }

    /**
     * Gets the set of view class names that should be explicitly unmasked during session replay.
     *
     * @return The set of view class names to unmask
     */
    public Set<String> getUnmaskedViewClasses() {
        if (unmaskedViewClasses == null) {
            unmaskedViewClasses = new HashSet<>();
        }
        return unmaskedViewClasses;
    }

    /**
     * Gets the set of view tags that should be masked during session replay.
     *
     * @return The set of view tags to mask
     */
    public Set<String> getMaskedViewTags() {
        if (maskedViewTags == null) {
            maskedViewTags = new HashSet<>();
        }
        return maskedViewTags;
    }

    /**
     * Gets the set of view tags that should be explicitly unmasked during session replay.
     *
     * @return The set of view tags to unmask
     */
    public Set<String> getUnmaskedViewTags() {
        if (unmaskedViewTags == null) {
            unmaskedViewTags = new HashSet<>();
        }
        return unmaskedViewTags;
    }

    /**
     * Adds a view class name to the list of views that should have their text masked
     * during session replay.
     *
     * @param viewClassName The fully qualified class name of the view to mask
     */
    public void addMaskViewClass(String viewClassName) {
        if (maskedViewClasses == null) {
            maskedViewClasses = new HashSet<>();
        }
        maskedViewClasses.add(viewClassName);
    }

    /**
     * Adds a view class name to the list of views that should be explicitly unmasked
     * during session replay, even if they would otherwise be masked.
     *
     * @param viewClassName The fully qualified class name of the view to unmask
     */
    public void addUnmaskViewClass(String viewClassName) {
        if (unmaskedViewClasses == null) {
            unmaskedViewClasses = new HashSet<>();
        }
        unmaskedViewClasses.add(viewClassName);
    }

    /**
     * Adds a view tag to the list of views that should have their text masked
     * during session replay.
     *
     * @param viewTag The tag value to mask
     */
    public void addMaskViewTag(String viewTag) {
        if (maskedViewTags == null) {
            maskedViewTags = new HashSet<>();
        }
        maskedViewTags.add(viewTag);
    }

    /**
     * Adds a view tag to the list of views that should be explicitly unmasked
     * during session replay, even if they would otherwise be masked.
     *
     * @param viewTag The tag value to unmask
     */
    public void addUnmaskViewTag(String viewTag) {
        if (unmaskedViewTags == null) {
            unmaskedViewTags = new HashSet<>();
        }
        unmaskedViewTags.add(viewTag);
    }


    /**
     * Checks if a view tag should be masked.
     *
     * @param viewTag The tag to check
     * @return true if the view should be masked, false otherwise
     */
    public boolean shouldMaskViewTag(String viewTag) {
        return maskedViewTags != null && maskedViewTags.contains(viewTag);
    }

    /**
     * Checks if a view tag should be explicitly unmasked.
     *
     * @param viewTag The tag to check
     * @return true if the view should be unmasked, false otherwise
     */
    public boolean shouldUnmaskViewTag(String viewTag) {
        return unmaskedViewTags != null && unmaskedViewTags.contains(viewTag);
    }

    /**
     * Gets whether application text should be masked during session replay.
     *
     * @return true if application text should be masked, false otherwise
     */
    public boolean isMaskApplicationText() {
        return maskApplicationText;
    }

    /**
     * Gets whether user input text should be masked during session replay.
     *
     * @return true if user input text should be masked, false otherwise
     */
    public boolean isMaskUserInputText() {
        return maskUserInputText;
    }


}