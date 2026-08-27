package com.newrelic.agent.android.webView;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class WebViewJSInterfaceTest {

    private WebViewJSInterface jsInterface;

    @Before
    public void setUp() {
        jsInterface = new WebViewJSInterface();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
    }

    @After
    public void tearDown() {
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
    }

    @Test
    public void reportBrowserAgentDetected_true_incrementsMetric() {
        jsInterface.reportBrowserAgentDetected(true);

        assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap()
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED));
    }

    @Test
    public void reportBrowserAgentDetected_false_doesNotIncrementMetric() {
        jsInterface.reportBrowserAgentDetected(false);

        assertFalse(StatsEngine.SUPPORTABILITY.getStatsMap()
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED));
    }
}
