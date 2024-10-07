package com.newrelic.agent.android

import com.newrelic.agent.android.analytics.AnalyticsAttribute
import com.newrelic.agent.android.analytics.AnalyticsAttributeStore
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl
import com.newrelic.agent.android.analytics.EventListener
import com.newrelic.agent.android.analytics.EventManager
import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.payload.Payload
import com.newrelic.agent.android.test.spy.AgentDataReporterSpy
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner


@RunWith(RobolectricTestRunner::class)
class NewRelicKotlinTest {

    private var nrInstance: NewRelic? = null
    private var spyContext: SpyContext? = null
    val APP_TOKEN: String = "ab20dfe5-96d2-4c6d-b975-3fe9d8778dfc"
    lateinit var analyticsController: AnalyticsControllerImpl
    lateinit var eventManager: EventManager

    @Before
    fun setUp() {
        spyContext = SpyContext()

        NewRelic.started = false
        NewRelic.isShutdown = false

        nrInstance =
            NewRelic.withApplicationToken(APP_TOKEN).withLogLevel(AgentLog.DEBUG)
        Assert.assertNotNull(nrInstance)

        NewRelic.enableFeature(FeatureFlag.HttpResponseBodyCapture)
        NewRelic.enableFeature(FeatureFlag.CrashReporting)
        NewRelic.enableFeature(FeatureFlag.AnalyticsEvents)
        NewRelic.enableFeature(FeatureFlag.InteractionTracing)
        NewRelic.enableFeature(FeatureFlag.DefaultInteractions)
    }

    @Before
    fun setupAnalyticsController() {
        val agentConfig = AgentConfiguration()
        agentConfig.enableAnalyticsEvents = true
        agentConfig.analyticsAttributeStore = StubAnalyticsAttributeStore()

        analyticsController = AnalyticsControllerImpl.getInstance()
        AnalyticsControllerImpl.initialize(agentConfig, NullAgentImpl())

        eventManager = analyticsController.getEventManager()
        eventManager.empty()
        eventManager.setEventListener(eventManager as EventListener?)
    }

    @Test
    fun testRecordBreadCrumbWithMapOfMethod() {

        Assert.assertTrue(
            "MobileBreadcrumb event should be recorded",
            NewRelic.recordBreadcrumb(
                "Breadcrumb",
                mapOf("key" to "value"),
            )
        )
    }

    @Test
    fun testRecordBreadCrumbWithEmptyMapMethod() {

        Assert.assertTrue(
            "MobileBreadcrumb event should be recorded",
            NewRelic.recordBreadcrumb(
                "Breadcrumb",
                emptyMap(),
            )
        )
    }

    @Test
    fun testRecordCustomEventWithMapOfMethod() {

        Assert.assertTrue(
            "MobileCustomEvent event should be recorded",
            NewRelic.recordCustomEvent(
                "CustomEvent",
                mapOf("key" to "value"),
            )
        )
    }

    @Test
    fun testRecordCustomEventWithMapOfAndEventNameMethod() {

        Assert.assertTrue(
            "MobileCustomEvent event should be recorded",
            NewRelic.recordCustomEvent(
                "eventName",
                "CustomEvent",
                mapOf("key" to "value"),
            )
        )
    }

    @Test
    fun testRecordCustomEventWithEmptyMapMethod() {

        Assert.assertTrue(
            "MobileCustomEvent event should be recorded",
            NewRelic.recordCustomEvent(
                "CustomEvent",
                emptyMap(),
            )
        )
    }

    @Test
    fun testRecordCustomEventWithEmptyMapAndEventNameMethod() {

        Assert.assertTrue(
            "MobileCustomEvent event should be recorded",
            NewRelic.recordCustomEvent(
                "eventName",
                "CustomEvent",
                emptyMap(),
            )
        )
    }

    @Test
    fun testHandledExceptionWithAttributes() {
        val agentDataReporter = AgentDataReporterSpy.initialize(NewRelic.agentConfiguration)


        Assert.assertTrue(
            "Should queue exception with attributes for delivery", NewRelic.recordHandledException(
                Exception("testException"), mapOf("key" to "value")
            )
        )
        Mockito.verify(agentDataReporter, Mockito.times(1)).storeAndReportAgentData(
            ArgumentMatchers.any(
                Payload::class.java
            )
        )

        // verify null attribute set also
        Assert.assertTrue(
            "Should queue exception with null attributes for delivery",
            NewRelic.recordHandledException(
                Exception("testException"), emptyMap()
            )
        )
        Mockito.verify(agentDataReporter, Mockito.times(2)).storeAndReportAgentData(
            ArgumentMatchers.any(
                Payload::class.java
            )
        )
    }


    class StubAnalyticsAttributeStore : AnalyticsAttributeStore {
        override fun store(attribute: AnalyticsAttribute): Boolean {
            return true
        }

        override fun fetchAll(): List<AnalyticsAttribute> {
            return ArrayList()
        }

        override fun count(): Int {
            return 0
        }

        override fun clear() {
        }

        override fun delete(attribute: AnalyticsAttribute) {
        }
    }
}