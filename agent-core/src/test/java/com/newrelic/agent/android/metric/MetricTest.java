package com.newrelic.agent.android.metric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.measurement.MetricMeasurementFactory;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class MetricTest {
    private static MetricMeasurementFactory factory = new MetricMeasurementFactory();

    Metric metric;

    @BeforeClass
    public static void beforeClass() throws Exception {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        metric = new Metric("metric", "metric/Scope");
    }

    @Test
    public void testMetricValidation() {
        Metric metric3 = new Metric("UnitTest3");
        metric3.setName("ChangeName3");
        metric3.setScope("Scope3");
        metric3.setMin(1.0);
        metric3.setMax(2.0);
        metric3.setSumOfSquares(3.0);
        metric3.setTotal(4.0);
        metric3.setExclusive(5.0);
        metric3.setCount(6);

        Assert.assertEquals(metric3.getName(), "ChangeName3");
        Assert.assertEquals(metric3.getScope(), "Scope3");
        Assert.assertEquals(metric3.getMin(), 1.0, 0);
        Assert.assertEquals(metric3.getMax(), 2.0, 0);
        Assert.assertEquals(metric3.getSumOfSquares(), 3.0, 0);
        Assert.assertEquals(metric3.getTotal(), 4.0, 0);
        Assert.assertEquals(metric3.getExclusive(), 5.0, 0);
        Assert.assertEquals(metric3.getCount(), 6);
    }

    @Test
    public void testMetricSettors() {
        Metric metric1 = new Metric("UnitTest1");
        metric1.setName("ChangeName1");
        metric1.setScope("Scope1");
        metric1.setMin(-1.0);
        metric1.setMax(-1.0);
        metric1.setSumOfSquares(-1.0);
        metric1.setTotal(-1.0);
        metric1.setExclusive(-1.0);
        metric1.setCount(1);

        Assert.assertEquals(metric1.getName(), "ChangeName1");
        Assert.assertEquals(metric1.getScope(), "Scope1");
        Assert.assertEquals(metric1.getMin(), -1, 0);
        Assert.assertEquals(metric1.getMax(), -1, 0);
        Assert.assertEquals(metric1.getSumOfSquares(), 0.0, 0);
        Assert.assertEquals(metric1.getTotal(), -1, 0);
        Assert.assertEquals(metric1.getExclusive(), 0.0, 0);
        Assert.assertEquals(metric1.getCount(), 1);

    }

    @Test
    public void testMetricDefaults() {
        Metric metric2 = new Metric("UnitTest2");
        metric2.setName("ChangeName2");
        metric2.setScope("Scope2");
        metric2.setCount(2);

        Assert.assertEquals(metric2.getName(), "ChangeName2");
        Assert.assertEquals(metric2.getScope(), "Scope2");
        Assert.assertEquals(metric2.getMin(), 0.0, 0);
        Assert.assertEquals(metric2.getMax(), 0.0, 0);
        Assert.assertEquals(metric2.getSumOfSquares(), 0.0, 0);
        Assert.assertEquals(metric2.getTotal(), 0.0, 0);
        Assert.assertEquals(metric2.getExclusive(), 0.0, 0);
        Assert.assertEquals(metric2.getCount(), 2);
        Assert.assertTrue(metric2.isCountOnly());
    }

    @Test
    public void sample() {
        metric.sample(0.016000032424926758);
        Assert.assertEquals(1, metric.getCount());
        Assert.assertEquals(0.016000032424926758, metric.getTotal(), 0.);
    }

    @Test
    public void sampleSetMinMaxSoS() {
        double seed = 2.4999523168389715E-4;
        metric.sample(seed);
        Assert.assertEquals(1, metric.getCount());
        Assert.assertEquals(seed, metric.getTotal(), 0.);
        Assert.assertEquals(seed, metric.getMin(), 0.);
        Assert.assertEquals(seed, metric.getMax(), 0.);
        Assert.assertEquals(Math.pow(seed, 2), metric.getSumOfSquares(), 0.);
        Assert.assertEquals(0, metric.getExclusive(), 0.);
    }

    @Test
    public void sampleMetricDataUsage() {
        metric.sampleMetricDataUsage(2345, 6789);
        Assert.assertEquals(2345, metric.getTotal(), 0.);
        Assert.assertEquals(6789, metric.getExclusive(), 0.);
    }

    @Test
    public void setMin() {
        metric.setMin(Double.MAX_VALUE);
        Assert.assertEquals(Double.MAX_VALUE, metric.getMin(), 0.);

        metric.setMin(Double.MIN_VALUE);
        Assert.assertEquals(Double.MIN_VALUE, metric.getMin(), 0.);

        metric.setMin(0.);
        Assert.assertEquals(0, metric.getMin(), 0);

        metric.setMin(-123.4);
        Assert.assertEquals(-123.4, metric.getMin(), 0.);
    }

    @Test
    public void setMinFieldValue() {
        metric.setMinFieldValue(Double.MIN_VALUE);
        Assert.assertEquals(Double.MIN_VALUE, metric.getMin(), 0.);

        metric.setMinFieldValue(Double.MAX_VALUE);
        Assert.assertEquals(Double.MAX_VALUE, metric.getMin(), 0.);

        metric.setMinFieldValue(0.);
        Assert.assertEquals(0, metric.getMin(), 0.);
    }

    @Test
    public void setMax() {
        metric.setMax(Double.MIN_VALUE);
        Assert.assertEquals(Double.MIN_VALUE, metric.getMax(), 0.);
        Assert.assertTrue(Double.MIN_VALUE <= metric.getMax());

        metric.clear();
        metric.setMax(Double.MIN_VALUE);
        Assert.assertEquals(Double.MIN_VALUE, metric.getMax(), 0.);

        metric.clear();
        metric.setMax(Double.MAX_VALUE);
        Assert.assertEquals(Double.MAX_VALUE, metric.getMax(), 0.);
        Assert.assertTrue(Double.MAX_VALUE >= metric.getMax());

        metric.clear();
        metric.setMax(Double.MAX_VALUE);
        Assert.assertEquals(Double.MAX_VALUE, metric.getMax(), 0.);

        metric.clear();
        metric.setMax(0.1);
        Assert.assertEquals(0.1, metric.getMax(), 0.);
    }

    @Test
    public void setMaxFieldValue() {
        metric.setMaxFieldValue(Double.MIN_VALUE);
        Assert.assertEquals(Double.MIN_VALUE, metric.getMax(), 0.);

        metric.setMaxFieldValue(Double.MAX_VALUE);
        metric.sample(Double.MAX_VALUE + 1.);
        Assert.assertEquals(Double.MAX_VALUE, metric.getMax(), 0.);

        metric.setMinFieldValue(0.);
        Assert.assertEquals(0, metric.getMin(), 0.);
    }

    @Test
    public void aggregate() {
        final Metric aggregate = new Metric("aggregate");

        for (int i = 0; i < 5; i++) {
            Metric metric = factory.provideMetric();
            metric.clear();
            metric.sample(3.);
            metric.addExclusive(4.);
            aggregate.aggregate(metric);
        }

        Assert.assertEquals(5, aggregate.getCount(), 0.);
        Assert.assertEquals(15, aggregate.getTotal(), 0.);
        Assert.assertEquals(20, aggregate.getExclusive(), 0.);
        Assert.assertEquals(45, aggregate.getSumOfSquares(), 0.);
    }

    @Test
    public void increment() {
        metric.increment();
        metric.increment(2);
        metric.increment(3);
        Assert.assertEquals(6, metric.getCount());
    }

    @Test
    public void incrementWithNegativeValue() {
        metric.increment();
        metric.increment(2);
        metric.increment(3);
        Assert.assertEquals(6, metric.getCount());

        metric.increment(-7);
        Assert.assertEquals(6, metric.getCount());
    }

    @Test
    public void getSumOfSquares() {
        double sumOfSquares = 0.;

        for (int i = 1; i <= 5; i++) {
            sumOfSquares += Math.pow(i, 2);
            metric.sample(i);
        }
        Assert.assertEquals(55f, sumOfSquares, 0.);
        Assert.assertEquals(sumOfSquares, metric.getSumOfSquares(), 0.);
    }

    @Test
    public void testNegativeSumOfSquares() {
        double sumOfSquares = 0.;

        for (int i = -1; i >= -5; i--) {
            sumOfSquares += Math.pow(i, 2);
            metric.sample(i);
        }
        Assert.assertEquals(55f, sumOfSquares, 0.);
        Assert.assertEquals(sumOfSquares, metric.getSumOfSquares(), 0.);
    }

    @Test
    public void getCount() {
        metric.sample(1);
        metric.sample(2);
        metric.increment(-3);
        metric.sample(4);
        metric.sample(5);
        metric.sampleMetricDataUsage(1., 2.);

        Assert.assertEquals(5, metric.getCount());

        metric.setCount(11);
        Assert.assertEquals(11, metric.getCount());
    }

    @Test
    public void getExclusive() {
        metric.setExclusive(-3.456);
        Assert.assertEquals(0., metric.getExclusive(), 0.);

        metric.setExclusive(3.);
        metric.addExclusive(-3.);
        Assert.assertEquals(0., metric.getExclusive(), 0.);
    }

    @Test
    public void addExclusive() {
        metric.setExclusive(3.);
        metric.addExclusive(3.);
        metric.addExclusive(-3.);
        Assert.assertEquals(3., metric.getExclusive(), 0.);

        metric.addExclusive(-6.);
        Assert.assertEquals(-3., metric.getExclusive(), 0.);
    }

    @Test
    public void getName() {
        metric.setName(null);
        Assert.assertNotNull(metric.getName());

        metric.setName("metric/Name");
        Assert.assertEquals("metric/Name", metric.getName());
    }

    @Test
    public void setName() {
        metric.setName(null);
        metric.setName("metric/Name");
        Assert.assertNotNull(metric.getName());

        Assert.assertTrue(metric.isScoped());
        Assert.assertEquals("metric/Name", metric.getName());
    }

    @Test
    public void getScope() {
        Assert.assertEquals("metric/Scope", metric.getScope());
        Assert.assertTrue(metric.isScoped());
        Assert.assertFalse(metric.isUnscoped());
    }

    @Test
    public void getStringScope() {
        metric.setScope(null);
        Assert.assertEquals("", metric.getStringScope());
        Assert.assertTrue(metric.isUnscoped());

        metric.setScope("scoped/metric");
        Assert.assertEquals("scoped/metric", metric.getScope());
        Assert.assertEquals("scoped/metric", metric.getStringScope());
    }

    @Test
    public void setScope() {
        metric.setScope(null);
        Assert.assertEquals(null, metric.getScope());
        Assert.assertTrue(metric.isUnscoped());

        metric.setScope("scoped/metric");
        Assert.assertEquals("scoped/metric", metric.getScope());
        Assert.assertFalse(metric.isUnscoped());
    }

    @Test
    public void getTotal() {
        metric.sample(123.);
        metric.sample(123.);
        metric.sample(123.);
        Assert.assertEquals(369, metric.getTotal(), 0.);
    }

    @Test
    public void setTotal() {
        metric.sample(123.);
        metric.sample(123.);
        metric.sample(123.);
        Assert.assertEquals(369, metric.getTotal(), 0.);

        metric.setTotal(123.);
        Assert.assertEquals(123, metric.getTotal(), 0.);
    }

    @Test
    public void setSumOfSquares() {
        metric.setSumOfSquares(1.23);
        Assert.assertEquals(1.23, metric.getSumOfSquares(), 0.);

        metric.clear();
        metric.sample(2.);
        metric.sample(3.);
        metric.sample(4.);
        metric.sample(5.);
        metric.sample(6.);

        Assert.assertEquals(90, metric.getSumOfSquares(), 0.);
    }

    @Test
    public void setExclusive() {
        metric.setExclusive(-9.);
        Assert.assertEquals(0., metric.getExclusive(), 0.);

        metric.setExclusive(9.);
        metric.addExclusive(9.);
        Assert.assertEquals(18., metric.getExclusive(), 0.);
    }

    @Test
    public void setCount() {
        metric.setCount(-98765);
        Assert.assertEquals(0., metric.getCount(), 0.);
        Assert.assertTrue(metric.isCountOnly());

        metric.setCount(Double.valueOf(1.9999998765).longValue());
        Assert.assertEquals(1., metric.getCount(), 0.);
        Assert.assertTrue(metric.isCountOnly());
    }

    @Test
    public void clear() {
        metric.clear();
        Assert.assertEquals(0., metric.getTotal(), 0.);
        Assert.assertEquals(0., metric.getCount(), 0.);
        Assert.assertEquals(0., metric.getMin(), 0.);
        Assert.assertEquals(0., metric.getMax(), 0.);
        Assert.assertEquals(0., metric.getSumOfSquares(), 0.);
        Assert.assertEquals(0., metric.getExclusive(), 0.);
        Assert.assertEquals(0., metric.getTotal(), 0.);

        Assert.assertEquals("metric/Scope", metric.getScope());
    }

    @Test
    public void isCountOnly() {
        Assert.assertTrue(metric.isCountOnly());
        metric.increment();
        metric.increment(1);
        metric.increment(-1);
        Assert.assertTrue(metric.isCountOnly());
        metric.sample(1.);
        Assert.assertFalse(metric.isCountOnly());
    }

    @Test
    public void testIsCountOnlyAsJson() {
        metric.increment();
        metric.increment();
        metric.increment();
        metric.increment(-1);
        Assert.assertEquals(3, metric.getCount());
        Assert.assertTrue(metric.isCountOnly());

        JsonElement json = metric.asJson();
        Assert.assertNotNull(json);
        Assert.assertTrue(json.isJsonPrimitive());
        Assert.assertEquals(3, json.getAsLong());
    }

    @Test
    public void isScoped() {
        Assert.assertTrue(metric.isScoped());
        metric.setScope(null);
        Assert.assertTrue(metric.isUnscoped());
    }

    @Test
    public void isUnscoped() {
        Assert.assertTrue(metric.isScoped());
        metric.setScope(null);
        Assert.assertTrue(metric.isUnscoped());
    }

    @Test
    public void asJson() {
        metric.sample(-98.765);
        metric.setExclusive(metric.getTotal() / 2.);

        JsonElement json = metric.asJson();
        Assert.assertNotNull(json);
        Assert.assertEquals("{\"count\":1,\"total\":-98.765,\"min\":-98.765,\"max\":-98.765,\"sum_of_squares\":9754.525225,\"exclusive\":0.0}", json.toString());
    }

    @Test
    public void asJsonObject() {
        metric.sample(98.765);

        JsonObject json = metric.asJsonObject();
        Assert.assertNotNull(json);
        Assert.assertEquals(6, json.entrySet().size());

        Assert.assertEquals("{\"count\":1,\"total\":98.765,\"min\":98.765,\"max\":98.765,\"sum_of_squares\":9754.525225,\"exclusive\":0.0}", json.toString());
    }

    @Test
    public void testToString() {
        metric.sample(1);
        metric.increment(1);
        metric.increment(1);

        String str = metric.toString();
        Assert.assertNotNull(str);
        Assert.assertEquals("Metric{count=3, total=1.0, max=1.0, min=1.0, scope='metric/Scope', name='metric', exclusive='0.0', sumofsquares='1.0'}", str);
    }

    @Test
    public void testJsonFromSampleDefaults() {
        double seed = 4.;

        metric.sample(seed);

        JsonObject json = metric.asJsonObject();
        Assert.assertNotNull(json);
        Assert.assertNotNull(json.get("count"));
        Assert.assertNotNull(json.get("total"));
        Assert.assertNotNull(json.get("min"));
        Assert.assertNotNull(json.get("max"));
        Assert.assertNotNull(json.get("sum_of_squares"));
        Assert.assertNotNull(json.get("exclusive"));

        Assert.assertEquals(1, json.get("count").getAsLong());
        Assert.assertEquals(metric.getTotal(), json.get("total").getAsDouble(), 0.);
        Assert.assertEquals(metric.getMin(), json.get("min").getAsDouble(), 0.);
        Assert.assertEquals(metric.getMax(), json.get("max").getAsDouble(), 0.);
        Assert.assertEquals(metric.getSumOfSquares(), json.get("sum_of_squares").getAsDouble(), 0.);
        Assert.assertEquals(metric.getExclusive(), json.get("exclusive").getAsDouble(), 0.);
    }

    @Test
    public void testJsonFromAggregateDefaults() {
        double seed = 4.;
        Metric providedMetric = factory.provideMetric();

        metric.sample(seed);
        metric.aggregate(providedMetric);

        JsonObject json = metric.asJsonObject();
        Assert.assertNotNull(json);
        Assert.assertNotNull(json.get("count"));
        Assert.assertNotNull(json.get("total"));
        Assert.assertNotNull(json.get("min"));
        Assert.assertNotNull(json.get("max"));
        Assert.assertNotNull(json.get("sum_of_squares"));
        Assert.assertNotNull(json.get("exclusive"));

        Assert.assertEquals(2, json.get("count").getAsLong());
        Assert.assertTrue(json.get("total").getAsDouble() > providedMetric.getTotal());
        Assert.assertTrue(json.get("min").getAsDouble() > 0.);
        Assert.assertTrue(json.get("min").getAsDouble() == metric.getMin() || json.get("min").getAsDouble() == providedMetric.getMin());
        Assert.assertTrue(json.get("max").getAsDouble() > 0.);
        Assert.assertTrue(json.get("max").getAsDouble() == metric.getMax() || json.get("max").getAsDouble() == providedMetric.getMax());
        Assert.assertEquals(Math.pow(seed, 2) + providedMetric.getSumOfSquares(), json.get("sum_of_squares").getAsDouble(), 0.);
        Assert.assertEquals(metric.getExclusive() + providedMetric.getExclusive(), json.get("exclusive").getAsDouble(), 0.);
    }

    @Test
    public void testNegativeMetricValues() {
        metric.increment();
        Assert.assertEquals(1, metric.getCount());

        metric.increment(-1);
        Assert.assertEquals(1, metric.getCount());

        metric.sample(-2);
        Assert.assertEquals(2, metric.getCount());
        Assert.assertEquals(-2., metric.getTotal(), 0.);
        Assert.assertEquals(4., metric.getSumOfSquares(), 0.);

        metric.sample(-2);
        Assert.assertEquals(-4., metric.getTotal(), 0.);
        Assert.assertEquals(8., metric.getSumOfSquares(), 0.);

        Metric invertedMetric = new Metric("inverted");
        invertedMetric.setTotal(metric.getTotal() * 1.);
        invertedMetric.setMin(metric.getMin() * 1.);
        invertedMetric.setMax(metric.getMax() * 1.);
        invertedMetric.setExclusive(-4.567);

        metric.aggregate(invertedMetric);
        Assert.assertEquals(3, metric.getCount());
        Assert.assertEquals(-4., metric.getTotal(), 0.);
        Assert.assertEquals(0., metric.getExclusive(), 0.);
    }

    @Test
    public void testTotalLessThanMax() {
        metric.sample(2.);
        metric.sample(7.);  // outlier
        metric.sample(-5.);

        Assert.assertTrue(metric.getTotal() <= metric.getMax());
    }

    @Test
    public void testTotalGreaterThanMin() {
        metric.sample(2.);
        metric.sample(7.);  // outlier
        metric.sample(0);

        Assert.assertTrue(metric.getTotal() > metric.getMin());
        Assert.assertEquals(0., metric.getMin(), 0.);
    }

    @Test
    public void testCountingMetrics() {
        metric.increment();
        metric.increment(3);
        metric.increment(-2);

        Assert.assertTrue(metric.isCountOnly());

        JsonObject json = metric.asJsonObject();
        Assert.assertNotNull(json);
        Assert.assertEquals(1, json.entrySet().size());
        Assert.assertEquals("{\"count\":4}", json.toString());

        Metric anotherCountingMetric = new Metric("counting");
        anotherCountingMetric.increment(2);
        Assert.assertTrue(anotherCountingMetric.isCountOnly());

        metric.aggregate(anotherCountingMetric);
        Assert.assertTrue(metric.isCountOnly());

        metric.sample(0.1234);
        Assert.assertFalse(metric.isCountOnly());

        anotherCountingMetric.aggregate(metric);
        Assert.assertTrue(anotherCountingMetric.isCountOnly());
    }

}

