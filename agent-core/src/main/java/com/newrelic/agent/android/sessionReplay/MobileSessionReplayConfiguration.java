/**
 * Copyright 2023-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MobileSessionReplayConfiguration {

    @SerializedName("enabled")
    private boolean enabled;

    @SerializedName("sampling_rate")
    private double samplingRate;

    @SerializedName("error_sampling_rate")
    private double errorSamplingRate;

    @SerializedName("mode")
    private String mode;

    @SerializedName("maskApplicationText")
    private boolean maskApplicationText;

    @SerializedName("maskUserInputText")
    private boolean maskUserInputText;

    @SerializedName("maskAllUserTouches")
    private boolean maskAllUserTouches;

    @SerializedName("maskAllImages")
    private boolean maskAllImages;

    @SerializedName("customMaskingRules")
    private List<CustomMaskingRule> customMaskingRules;

    static Double sampleSeed = 100.000000;

    private TextMaskingStrategy textMaskingStrategy;


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

    public MobileSessionReplayConfiguration() {
        // Default values
        this.enabled = false;
        this.samplingRate = 0.0;
        this.errorSamplingRate = 100.0;
        this.mode = "custom";
        this.maskApplicationText = true;
        this.maskUserInputText = true;
        this.maskAllUserTouches = true;
        this.maskAllImages = true;
        this.customMaskingRules = new ArrayList<>();
        this.textMaskingStrategy = TextMaskingStrategy.MASK_ALL_TEXT;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getSamplingRate() {
        return samplingRate;
    }

    public void setSamplingRate(double samplingRate) {
        this.samplingRate = samplingRate;
    }

    public double getErrorSamplingRate() {
        return errorSamplingRate;
    }

    public void setErrorSamplingRate(double errorSamplingRate) {
        this.errorSamplingRate = errorSamplingRate;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isMaskApplicationText() {
        return textMaskingStrategy == TextMaskingStrategy.MASK_ALL_TEXT;
    }

    public void setMaskApplicationText(boolean maskApplicationText) {
        if (maskApplicationText) {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_ALL_TEXT;
        } else if (maskUserInputText) {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_USER_INPUT_TEXT;
        } else {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_NO_TEXT;
        }
    }

    public boolean isMaskUserInputText() {
        return textMaskingStrategy == TextMaskingStrategy.MASK_ALL_TEXT || 
               textMaskingStrategy == TextMaskingStrategy.MASK_USER_INPUT_TEXT;
    }

    public void setMaskUserInputText(boolean maskUserInputText) {
        if (this.textMaskingStrategy == TextMaskingStrategy.MASK_ALL_TEXT) {
            // Keep MASK_ALL_TEXT strategy if it's already set
            return;
        }
        
        this.textMaskingStrategy = maskUserInputText ? 
            TextMaskingStrategy.MASK_USER_INPUT_TEXT : 
            TextMaskingStrategy.MASK_NO_TEXT;
    }

    public boolean isMaskAllUserTouches() {
        return maskAllUserTouches;
    }

    public void setMaskAllUserTouches(boolean maskAllUserTouches) {
        this.maskAllUserTouches = maskAllUserTouches;
    }

    public boolean isMaskAllImages() {
        return maskAllImages;
    }

    public void setMaskAllImages(boolean maskAllImages) {
        this.maskAllImages = maskAllImages;
    }

    public List<CustomMaskingRule> getCustomMaskingRules() {
        return customMaskingRules;
    }

    public void setCustomMaskingRules(List<CustomMaskingRule> customMaskingRules) {
        this.customMaskingRules = customMaskingRules;
    }

    public boolean isSessionReplayEnabled() {
        return this.enabled && isSampled();
    }

    /**
     * @return true if the generated sample seed is less than or equal to the configured sampling rate
     */
    public boolean isSampled() {
        return sampleSeed <= samplingRate;
    }

    public Set<String> getMaskedViewTags() {
        if (maskedViewTags == null) {
            maskedViewTags = new HashSet<>();
        }
        
        customMaskingRules.forEach(rule -> {
            if (rule.getOperator().equals("equals") && rule.getType().equals("mask")) {
                maskedViewTags.addAll(rule.getName());
            }
        });
        return maskedViewTags;
    }

    public Set<String> getUnMaskedViewTags() {
        if (unmaskedViewTags == null) {
            unmaskedViewTags = new HashSet<>();
        }
        
        customMaskingRules.forEach(rule -> {
            if (rule.getOperator().equals("equals") && rule.getType().equals("un-mask")) {
                unmaskedViewTags.addAll(rule.getName());
            }
        });
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
     * Checks if a view class should be masked based on its class name.
     *
     * @param viewClassName The class name to check
     * @return true if the view should be masked, false otherwise
     */
    public boolean shouldMaskViewClass(String viewClassName) {
        // If explicitly masked, do mask
        return maskedViewClasses != null && maskedViewClasses.contains(viewClassName);
    }

    public boolean shouldUnmaskViewClass(String viewClassName) {
        // If explicitly unmasked, do not mask
        return unmaskedViewClasses != null && unmaskedViewClasses.contains(viewClassName);
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
        getMaskedViewTags();
        
        // If explicitly masked, do mask
        return maskedViewTags != null && maskedViewTags.contains(viewTag);
    }

    public boolean shouldUnmaskViewTag(String viewTag) {
        getUnMaskedViewTags();

        // If explicitly unmasked, do not mask
        return unmaskedViewTags != null && unmaskedViewTags.contains(viewTag);
    }


    /**
     * Generate a suitable seed. Range is [1...100];
     */
    public static Double reseed() {
        sampleSeed = Math.round((Math.random()*100*1000000))/1000000.0;
        return sampleSeed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MobileSessionReplayConfiguration that = (MobileSessionReplayConfiguration) o;
        return enabled == that.enabled &&
                Double.compare(that.samplingRate, samplingRate) == 0 &&
                Double.compare(that.errorSamplingRate, errorSamplingRate) == 0 &&
                maskApplicationText == that.maskApplicationText &&
                maskUserInputText == that.maskUserInputText &&
                maskAllUserTouches == that.maskAllUserTouches &&
                maskAllImages == that.maskAllImages &&
                Objects.equals(mode, that.mode) &&
                Objects.equals(customMaskingRules, that.customMaskingRules) &&
                textMaskingStrategy == that.textMaskingStrategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, samplingRate, errorSamplingRate, mode, maskApplicationText, 
                maskUserInputText, maskAllUserTouches, maskAllImages, customMaskingRules, textMaskingStrategy);
    }

    public void setConfiguration(MobileSessionReplayConfiguration sessionReplayConfiguration) {
        if (!sessionReplayConfiguration.equals(this)) {
            enabled = sessionReplayConfiguration.enabled;
            samplingRate = sessionReplayConfiguration.samplingRate;
            errorSamplingRate = sessionReplayConfiguration.errorSamplingRate;
            mode = sessionReplayConfiguration.mode;
            maskApplicationText = sessionReplayConfiguration.maskApplicationText;
            maskUserInputText = sessionReplayConfiguration.maskUserInputText;
            maskAllUserTouches = sessionReplayConfiguration.maskAllUserTouches;
            maskAllImages = sessionReplayConfiguration.maskAllImages;
            customMaskingRules = new ArrayList<>(sessionReplayConfiguration.customMaskingRules);
            textMaskingStrategy = sessionReplayConfiguration.textMaskingStrategy;
        }

    }

    /**
     * Gets the current text masking strategy.
     *
     * @return The current text masking strategy
     */
    public TextMaskingStrategy getTextMaskingStrategy() {
        return this.textMaskingStrategy;
    }

    /**
     * Sets the text masking strategy to use for session replay.
     *
     * @param strategy The text masking strategy to apply
     */
    public void setTextMaskingStrategy(TextMaskingStrategy strategy) {
        this.textMaskingStrategy = strategy;
    }

    /**
     * Gets the set of view class names that should be masked during session replay.
     *
     * @return The set of view class names to mask, or an empty set if none are defined
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
     * @return The set of view class names to unmask, or an empty set if none are defined
     */
    public Set<String> getUnmaskedViewClasses() {
        if (unmaskedViewClasses == null) {
            unmaskedViewClasses = new HashSet<>();
        }
        return unmaskedViewClasses;
    }

    public static class CustomMaskingRule {
        @SerializedName("identifier")
        private String identifier;

        @SerializedName("name")
        private List<String> name;

        @SerializedName("operator")
        private String operator;

        @SerializedName("type")
        private String type;

        public CustomMaskingRule() {
            this.name = new ArrayList<>();
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public List<String> getName() {
            return name;
        }

        public void setName(List<String> name) {
            this.name = name;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        /**
         * @return true is remote logging is enabled AND this session is sampling
         */

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomMaskingRule that = (CustomMaskingRule) o;
            return Objects.equals(identifier, that.identifier) &&
                    Objects.equals(name, that.name) &&
                    Objects.equals(operator, that.operator) &&
                    Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier, name, operator, type);
        }
    }
}