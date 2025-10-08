/*
 * Copyright 2022 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.metric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableObject;

public class Metric extends HarvestableObject {
    private String name;
    private String scope;
    private Double min;
    private Double max;
    private Double total;
    private Double sumOfSquares;
    private Double exclusive;
    private long count;

    // Here are the things we'll need to send back to the collector.  Note that the average and sum of squares are only
    // computed on demand.
    // name, scope, average, count, min, max, sum of squares, exclusive

    public Metric(String name) {
        this(name, null);
    }

    public Metric(String name, String scope) {
        super();

        this.name = name;
        this.scope = scope;

        count = 0;
    }

    public Metric(Metric metric) {
        name = metric.getName();
        scope = metric.getScope();
        min = metric.getMin();
        max = metric.getMax();
        total = metric.getTotal();
        sumOfSquares = metric.getSumOfSquares();
        exclusive = metric.getExclusive();
        count = metric.getCount();
    }

    public void sample(double value) {
        count++;

        if (total == null) {
            total = value;
            sumOfSquares = value * value;
        } else {
            total += value;
            sumOfSquares += value * value;
        }

        setMin(value);
        setMax(value);
    }

    public void sampleMetricDataUsage(double bytesSent, double byteReceived) {
        //interaction count
        count++;

        //bytesSent
        if (total == null) {
            total = bytesSent;
        } else {
            total += bytesSent;
        }

        //bytesReceived
        if (exclusive == null) {
            exclusive = byteReceived;
        } else {
            exclusive += byteReceived;
        }

        //the rest is unused, should be 0 by default
        sumOfSquares = 0.0;
        min = 0.0;
        max = 0.0;
    }

    public void setMin(Double value) {
        if (value == null)
            return;

        if (min == null) {
            min = value;
        } else {
            if (value < min) {
                min = value;
            }
        }
    }

    public void setMinFieldValue(Double value) {
        min = value;
    }

    public void setMax(Double value) {
        if (value == null)
            return;

        if (max == null) {
            max = value;
        } else {
            if (value > max) {
                max = value;
            }
        }
    }

    public void setMaxFieldValue(Double value) {
        max = value;
    }

    public void aggregate(Metric metric) {
        if (metric == null)
            return;

        increment(metric.getCount());

        if (metric.isCountOnly()) {
            return;
        }

        total = total == null ? metric.getTotal() : total + metric.getTotal();
        sumOfSquares = sumOfSquares == null ? metric.getSumOfSquares() : sumOfSquares + metric.getSumOfSquares();
        exclusive = exclusive == null ? metric.getExclusive() : exclusive + metric.getExclusive();

        setMin(metric.getMin());
        setMax(metric.getMax());
    }

    public void increment(long value) {
        count += value;
    }

    public void increment() {
        increment(1);
    }

    public double getSumOfSquares() {
        return (sumOfSquares == null || sumOfSquares < 0) ? 0.0 : sumOfSquares;
    }

    public long getCount() {
        return count;
    }

    public double getExclusive() {
        return (exclusive == null || exclusive < 0) ? 0.0 : exclusive;
    }

    public void addExclusive(double value) {
        if (exclusive == null) {
            exclusive = value;
        } else {
            exclusive += value;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScope() {
        return scope;
    }

    public String getStringScope() {
        return scope == null ? "" : scope;
    }


    public double getMin() {
        return (min == null || min < 0) ? 0.0 : min;
    }

    public double getMax() {
        return (max == null || max < 0) ? 0.0 : max;
    }

    public double getTotal() {
        return (total == null || total < 0) ? 0.0 : total;
    }


    public void setScope(String scope) { this.scope = scope; }

    public void setTotal(Double total) { this.total = total; }

    public void setSumOfSquares(Double sumOfSquares) {
        this.sumOfSquares = sumOfSquares;
    }

    public void setExclusive(Double exclusive) {
        this.exclusive = exclusive;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public void clear() {
        min = null;
        max = null;
        total = null;
        sumOfSquares = null;
        exclusive = null;
        count = 0;
        scope = null;
    }

    public boolean isCountOnly() {
        return total == null;
    }

    public boolean isScoped() {
        return scope != null;
    }

    public boolean isUnscoped() {
        return scope == null;
    }

    @Override
    public JsonElement asJson() {
        if (isCountOnly()) {
            return new JsonPrimitive(count);
        }
        return asJsonObject();
    }

    @Override
    // Name and scope are encoded separately
    public JsonObject asJsonObject() {
        JsonObject jsonObject = new JsonObject();

        jsonObject.add("count", new JsonPrimitive(count));
        if (total != null)
            jsonObject.add("total", new JsonPrimitive(total));
        if (min != null)
            jsonObject.add("min", new JsonPrimitive(min));
        if (max != null)
            jsonObject.add("max", new JsonPrimitive(max));
        if (sumOfSquares != null)
            jsonObject.add("sum_of_squares", new JsonPrimitive(sumOfSquares));
        if (exclusive != null)
            jsonObject.add("exclusive", new JsonPrimitive(exclusive));

        return jsonObject;
    }

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
