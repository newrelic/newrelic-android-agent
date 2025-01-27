package com.newrelic.agent.android.metric;

import org.junit.Assert;
import org.junit.Test;

public class MetricTest {

    @Test
    public void testMetricValidation(){
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
        Assert.assertEquals(metric1.getMin(), 0.0, 0);
        Assert.assertEquals(metric1.getMax(), 0.0, 0);
        Assert.assertEquals(metric1.getSumOfSquares(), 0.0, 0);
        Assert.assertEquals(metric1.getTotal(), 0.0, 0);
        Assert.assertEquals(metric1.getExclusive(), 0.0, 0);
        Assert.assertEquals(metric1.getCount(), 1);

        Metric metric2 = new Metric("UnitTest2");
        metric2.setName("ChangeName2");
        metric2.setScope("Scope2");
        metric2.setMin(null);
        metric2.setMax(null);
        metric2.setSumOfSquares(null);
        metric2.setTotal(null);
        metric2.setExclusive(null);
        metric2.setCount(2);

        Assert.assertEquals(metric2.getName(), "ChangeName2");
        Assert.assertEquals(metric2.getScope(), "Scope2");
        Assert.assertEquals(metric2.getMin(), 0.0, 0);
        Assert.assertEquals(metric2.getMax(), 0.0, 0);
        Assert.assertEquals(metric2.getSumOfSquares(), 0.0, 0);
        Assert.assertEquals(metric2.getTotal(), 0.0, 0);
        Assert.assertEquals(metric2.getExclusive(), 0.0, 0);
        Assert.assertEquals(metric2.getCount(), 2);

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
}

