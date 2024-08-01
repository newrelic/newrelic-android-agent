/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.metric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableObject;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

/**
 * Construct a metric instance suitable for ingest. Must include name, scope, average, count, min, max, sum of squares, exclusive
 * <p>
 * Note that the average and sum of squares are only computed on demand.
 */
public class Metric extends HarvestableObject {
    final static AgentLog log = AgentLogManager.getAgentLog();

    private String name;
    private String scope;
    private Double min = Double.NaN;
    private Double max = Double.NaN;
    private Double total = 0.0;
    private Double sumOfSquares = 0.0;
    private Double exclusive = 0.0;
    private long count = 0;
    private boolean isCountOnly = true;


    /**
     * Construct a new metric using a provided name
     *
     * @param name Name of the metric
     */
    public Metric(String name) {
        this(name, null);
    }

    /**
     * Construct a new metric using a provided name and scope
     *
     * @param name  Name of the metric
     * @param scope Name of the metric scope
     */
    public Metric(String name, String scope) {
        super();

        this.name = name;
        this.scope = scope;
    }

    /**
     * Copy constructor
     *
     * @param metric Source metric value
     */
    public Metric(Metric metric) {
        name = metric.getName();
        scope = metric.getScope();
        min = metric.getMin();
        max = metric.getMax();
        total = metric.getTotal();
        sumOfSquares = metric.getSumOfSquares();
        exclusive = metric.getExclusive();
        count = metric.getCount();
        isCountOnly = metric.isCountOnly();
    }

    /**
     * Update the metric with the passed value:
     * . increment the count
     * . add the value's total to the metric
     * . add the value's sum of squares to the metric
     * . update the metric's min and max values
     *
     * @param value
     */
    public Metric sample(double value) {
        count++;
        total += value;
        isCountOnly = false;

        setMin(value);
        setMax(value);
        addSumOfSquares(value);

        return this;
    }

    public Metric sampleMetricDataUsage(double bytesSent, double byteReceived) {
        //interaction count
        count++;

        //bytesSent
        total += bytesSent;

        //bytesReceived
        exclusive += byteReceived;

        //the rest is unused, should be 0 by default
        sumOfSquares = 0.0;
        min = 0.0;
        max = 0.0;

        return this;
    }

    /**
     * Aggregate current metric with passed metric value. If this metric is a counter, the
     * passed metric's total, min, max, sumOfSquares and exclusive values are ignored.
     */
    public Metric aggregate(Metric metric) {
        if (metric != null) {
            increment(metric.getCount());

            if (!metric.isCountOnly()) {
                total += metric.getTotal();
                sumOfSquares += metric.getSumOfSquares();
                exclusive += metric.getExclusive();

                if (!metric.min.isNaN()) {
                    setMin(metric.min);
                }

                if (!metric.max.isNaN()) {
                    setMax(metric.max);
                }
            }
        } else {
            log.error("Metric.aggregate() called with null metric!");
        }

        return this;
    }

    /**
     * Increment the metric count by value. Negative values are ignored.
     *
     * @param value Additional count to add to this metric
     */
    public Metric increment(long value) {
        if (value > 0) {
            count += value;
        } else {
            log.error("Metric.increment() called with negative value[" + value + "]");
        }
        return this;
    }

    /**
     * Increment the metric count by 1.
     */
    public Metric increment() {
        increment(1);
        return this;
    }

    /**
     * Updates metrics sum of squares with value
     */
    public Metric addSumOfSquares(double value) {
        double valueSquared = Math.pow(value, 2);
        sumOfSquares = (sumOfSquares.isNaN() ? valueSquared : sumOfSquares + valueSquared);
        return this;
    }

    /**
     * Adds exclusive value to the metric
     */
    public Metric addExclusive(double value) {
        exclusive += value;
        return this;
    }


    /**
     * Return the unmodified name of the metric
     *
     * @return metric name
     */
    public String getName() {
        return name;
    }

    /**
     * Return the unmodified scope of the metric
     *
     * @return metric scope name
     */
    public String getScope() {
        return scope;
    }

    /**
     * Return the scope of the metric, or an empty string if scope is null.
     *
     * @return metric name
     */
    public String getStringScope() {
        return scope == null ? "" : scope;
    }

    /**
     * Return the unmodified minimum value of the metric
     *
     * @return metric min value
     */
    public double getMin() {
        return min.isNaN() ? 0.0 : min;
    }

    /**
     * Return the unmodified maximum value of the metric
     *
     * @return metric max value
     */
    public double getMax() {
        return max.isNaN() ? 0.0 : max;
    }

    /**
     * Return the unmodified total value of the metric
     *
     * @return metric total value
     */
    public double getTotal() {
        return total;
    }

    /**
     * Return the current sum of squares value for this metric.
     *
     * @return Current sum of squares value. Should never be negative.
     */
    public double getSumOfSquares() {
        return sumOfSquares;
    }

    /**
     * Return the current exclusive value for this metric. The value should not be negative.
     *
     * @return Current exclusive value
     */
    public double getExclusive() {
        return exclusive;
    }

    /**
     * Return the current call count value for this metric. The value should not be negative.
     *
     * @return Current count value
     */
    public long getCount() {
        return count;
    }


    /**
     * Set the name of the metric. Should not be null.
     *
     * @return this
     */
    public Metric setName(String name) {
        this.name = name == null ? "" : name;
        return this;
    }

    /**
     * Set the name of the metric. May be null.
     *
     * @return this
     */
    public Metric setScope(String scope) {
        this.scope = scope;
        return this;
    }

    /**
     * Sets the metric's minimum value if passed metric maximum is lesser or
     * the metric has not been set
     *
     * @return this
     */
    public Metric setMin(double value) {
        min = min.isNaN() ? value : Math.min(min, value);
        return this;
    }

    /**
     * Sets the metric's minimum value to the unmodified passed value
     */
    public Metric setMinFieldValue(double value) {
        min = value;
        return this;
    }

    /**
     * Sets the metric's maximum value if passed metric maximum is greater or
     * the metric has not been set.
     *
     * @return this
     */
    public Metric setMax(double value) {
        max = max.isNaN() ? value : Math.max(max, value);
        return this;
    }

    /**
     * Sets the metric's maximum value to the unmodified passed value
     *
     * @return this
     */
    public Metric setMaxFieldValue(double value) {
        max = value;
        return this;
    }

    /**
     * Set the unmodified total value of the metric.
     *
     * @return this
     */
    public Metric setTotal(double value) {
        this.total = value;
        return this;
    }

    /**
     * Set the unmodified sum of squares value of the metric. Should not be negative.
     *
     * @return this
     */
    public Metric setSumOfSquares(double value) {
        if (value >= 0) {
            sumOfSquares = value;
        } else {
            log.error("Metric.setSumOfSquares() called with negative value[" + value + "]");
        }
        return this;
    }

    /**
     * Set the unmodified exclusive value of the metric. Should not be negative.
     *
     * @return this
     */
    public Metric setExclusive(double value) {
        if (value >= 0) {
            exclusive = value;
        } else {
            log.error("Metric.setExclusive() called with negative value[" + value + "]");
        }
        return this;
    }

    /**
     * Set the count of the metric. The value should not be negative.
     *
     * @return this
     */
    public Metric setCount(long value) {
        if (value >= 0) {
            count = value;
        } else {
            log.error("Metric.setCount() called with negative value[" + value + "]");
        }
        return this;
    }


    /**
     * Reset the metric to default values
     */
    public void clear() {
        min = Double.NaN;
        max = Double.NaN;
        total = 0.0;
        sumOfSquares = 0.0;
        exclusive = 0.0;
        count = 0;
        isCountOnly = true;
    }

    /**
     * Return true if the metric is used to track counts only
     */
    public boolean isCountOnly() {
        return isCountOnly;
    }

    /**
     * Return true if the metric is scoped to a given name
     */
    public boolean isScoped() {
        return scope != null;
    }

    /**
     * Return false if the metric is scoped to a given name
     */
    public boolean isUnscoped() {
        return !isScoped();
    }

    /**
     * Serialize metric to Json
     *
     * @return JsonElement representing metric data
     */
    @Override
    public JsonElement asJson() {
        if (isCountOnly()) {
            return new JsonPrimitive(count);
        }
        return asJsonObject();
    }

    /**
     * Serialize metric to Json. Name and scope are encoded separately
     *
     * @return JsonElement representing metric data
     */
    @Override
    public JsonObject asJsonObject() {
        JsonObject jsonObject = new JsonObject();

        jsonObject.add("count", new JsonPrimitive(count));
        if (!isCountOnly) {
            jsonObject.add("total", new JsonPrimitive(total));
            if (!min.isNaN()) {
                jsonObject.add("min", new JsonPrimitive(min));
            }
            if (!max.isNaN()) {
                jsonObject.add("max", new JsonPrimitive(max));
            }
            jsonObject.add("sum_of_squares", new JsonPrimitive(sumOfSquares));
            jsonObject.add("exclusive", new JsonPrimitive(exclusive));
        }

        return jsonObject;
    }

    /**
     * Serialize metric to a String
     *
     * @return String representing metric data
     */
    @Override
    public String toString() {
        return "Metric{" +
                "count=" + count +
                ", total=" + total +
                ", max=" + max +
                ", min=" + min +
                ", scope='" + scope + '\'' +
                ", name='" + name + '\'' +
                ", exclusive='" + exclusive + '\'' +
                ", sumofsquares='" + sumOfSquares + '\'' +
                '}';
    }
}
