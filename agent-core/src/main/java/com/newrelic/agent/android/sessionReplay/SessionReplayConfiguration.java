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

public class SessionReplayConfiguration {

    @SerializedName("enabled")
    private boolean enabled;

    @SerializedName("sampling_rate")
    private double samplingRate;

    @SerializedName("error_sampling_rate")
    private double errorSamplingRate;

    @SerializedName("mode")
    private String mode;

    @SerializedName("mask_application_text")
    private boolean maskApplicationText;

    @SerializedName("mask_user_input_text")
    private boolean maskUserInputText;

    @SerializedName("mask_all_user_touches")
    private boolean maskAllUserTouches;

    @SerializedName("mask_all_images")
    private boolean maskAllImages;

    @SerializedName("custom_masking_rules")
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

    public SessionReplayConfiguration() {
        // Default values
        this.enabled = false;
        this.samplingRate = 10.0;
        this.errorSamplingRate = 100.0;
        this.mode = "default";
        this.maskApplicationText = true;
        this.maskUserInputText = true;
        this.maskAllUserTouches = false;
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
       return this.maskApplicationText;
    }

    public void setMaskApplicationText(boolean maskApplicationText) {
        this.maskApplicationText = maskApplicationText;
        if (this.maskApplicationText) {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_ALL_TEXT;
        } else if (this.maskUserInputText) {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_USER_INPUT_TEXT;
        } else {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_NO_TEXT;
        }
    }

    public boolean isMaskUserInputText() {
        return this.maskUserInputText;
    }

    public void setMaskUserInputText(boolean maskUserInputText) {
        this.maskUserInputText = maskUserInputText;

        if (this.maskApplicationText) {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_ALL_TEXT;
        } else if (this.maskUserInputText) {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_USER_INPUT_TEXT;
        } else {
            this.textMaskingStrategy = TextMaskingStrategy.MASK_NO_TEXT;
        }
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

    /**
     * Process custom masking rules to populate the view class and tag sets.
     * This should be called after deserialization from JSON.
     */
    public void processCustomMaskingRules() {
        // Initialize sets
        if (maskedViewTags == null) {
            maskedViewTags = new HashSet<>();
        } else {
            maskedViewTags.clear();
        }

        if (unmaskedViewTags == null) {
            unmaskedViewTags = new HashSet<>();
        } else {
            unmaskedViewTags.clear();
        }

        if (maskedViewClasses == null) {
            maskedViewClasses = new HashSet<>();
        } else {
            maskedViewClasses.clear();
        }

        if (unmaskedViewClasses == null) {
            unmaskedViewClasses = new HashSet<>();
        } else {
            unmaskedViewClasses.clear();
        }

        // Process rules if they exist
        if (customMaskingRules != null) {
            customMaskingRules.forEach(rule -> {
                if (rule != null && rule.getOperator() != null && rule.getType() != null && rule.getIdentifier() != null) {
                    if (rule.getOperator().equals("equals") && rule.getType().equals("mask") && rule.getIdentifier().equals("tag")) {
                        if (rule.getName() != null) {
                            maskedViewTags.addAll(rule.getName());
                        }
                    } else if (rule.getOperator().equals("equals") && rule.getType().equals("unmask") && rule.getIdentifier().equals("tag")) {
                        if (rule.getName() != null) {
                            unmaskedViewTags.addAll(rule.getName());
                        }
                    } else if (rule.getOperator().equals("equals") && rule.getType().equals("mask") && rule.getIdentifier().equals("class")) {
                        if (rule.getName() != null) {
                            maskedViewClasses.addAll(rule.getName());
                        }
                    } else if (rule.getOperator().equals("equals") && rule.getType().equals("unmask") && rule.getIdentifier().equals("class")) {
                        if (rule.getName() != null) {
                            unmaskedViewClasses.addAll(rule.getName());
                        }
                    }
                }
            });
        }
    }

    public boolean isSessionReplayEnabled() {
        return this.enabled;
    }

    /**
     * @return true if the generated sample seed is less than or equal to the configured sampling rate
     */
    public boolean isSampled() {
        return sampleSeed <= samplingRate;
    }

    public boolean isErrorSampled() {
        return sampleSeed <= errorSamplingRate;
    }



    /**
     * Checks if a view tag should be masked.
     *
     * @param viewTag The tag to check
     * @return true if the view should be masked, false otherwise
     */
    public boolean shouldMaskViewTag(String viewTag) {

        // If explicitly masked, do mask
        return maskedViewTags != null && maskedViewTags.contains(viewTag);
    }

    public boolean shouldUnmaskViewTag(String viewTag) {
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
        SessionReplayConfiguration that = (SessionReplayConfiguration) o;
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

    public void setConfiguration(SessionReplayConfiguration sessionReplayConfiguration) {
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
        return textMaskingStrategy;
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
        return maskedViewClasses;
    }

    /**
     * Gets the set of view class names that should be explicitly unmasked during session replay.
     *
     * @return The set of view class names to unmask, or an empty set if none are defined
     */
    public Set<String> getUnmaskedViewClasses() {
        return unmaskedViewClasses;
    }

    /**
     * Gets the set of view tags that should be masked during session replay.
     *
     * @return The set of view tags to mask, or an empty set if none are defined
     */
    public Set<String> getMaskedViewTags() {
        return maskedViewTags;
    }

    /**
     * Gets the set of view tags that should be explicitly unmasked during session replay.
     *
     * @return The set of view tags to unmask, or an empty set if none are defined
     */
    public Set<String> getUnmaskedViewTags() {
        return unmaskedViewTags;
    }




    @Override
    public String toString() {
        return "MobileSessionReplayConfiguration{" +
                "enabled=" + enabled +
                ", samplingRate=" + samplingRate +
                ", errorSamplingRate=" + errorSamplingRate +
                ", mode='" + mode + '\'' +
                ", maskApplicationText=" + maskApplicationText +
                ", maskUserInputText=" + maskUserInputText +
                ", maskAllUserTouches=" + maskAllUserTouches +
                ", maskAllImages=" + maskAllImages +
                ", textMaskingStrategy=" + textMaskingStrategy +
                ", customMaskingRules=" + customMaskingRules +
                ", maskedViewClasses=" + maskedViewClasses +
                ", unmaskedViewClasses=" + unmaskedViewClasses +
                ", maskedViewTags=" + maskedViewTags +
                ", unmaskedViewTags=" + unmaskedViewTags +
                '}';
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
        public String toString() {
            return "CustomMaskingRule{" +
                    "identifier='" + identifier + '\'' +
                    ", name=" + name +
                    ", operator='" + operator + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
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