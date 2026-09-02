package com.newrelic.agent.android.instrumentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.newrelic.agent.android.FeatureFlag
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl
import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.logging.AgentLogManager
import com.newrelic.agent.android.mobileview.MobileViewContext
import com.newrelic.agent.android.sessionReplay.SessionReplay
import  kotlin.collections.Map

private val log: AgentLog = AgentLogManager.getAgentLog()
/**
 * Adds a New Relic navigation listener to a NavHostController to track navigation events.
 * 
 * This extension function attaches a destination change listener to the NavHostController
 * that automatically records navigation routes as breadcrumbs and triggers full snapshots
 * in Session Replay when navigation occurs.
 *
 * @receiver The NavHostController to which the navigation listener will be attached
 * @return The same NavHostController instance with the New Relic navigation listener attached
 */
@Composable
fun NavHostController.withNewRelicNavigationListener(): NavHostController {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, this) {
        val observer = MeasureNavigationObserver(
            this@withNewRelicNavigationListener,
        )
        lifecycle.addObserver(observer)

        onDispose {
            observer.dispose()
            lifecycle.removeObserver(observer)
        }
    }
    return this
}

private class MeasureNavigationObserver(
    private val navController: NavController,
) : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            navController.addOnDestinationChangedListener(destinationChangedListener)
        } else if (event == Lifecycle.Event.ON_PAUSE) {
            navController.removeOnDestinationChangedListener(destinationChangedListener)
        }
    }

    fun dispose() {
        navController.removeOnDestinationChangedListener(destinationChangedListener)
    }

    // addOnDestinationChangedListener immediately re-invokes the listener with the CURRENT
    // destination at registration time, and this listener is re-registered on every ON_RESUME
    // (e.g. returning from background) - not just on real navigation. Track the last route
    // reported to MobileView so a same-route re-fire is skipped; otherwise every resume would
    // self-reference previousView=currentView=the same route, polluting the referrer chain.
    // Breadcrumb recording is intentionally left un-deduped - only MobileView emission is gated.
    private var lastMobileViewRoute: String? = null

    private val destinationChangedListener =
        NavController.OnDestinationChangedListener { controller, _, _ ->
            controller.currentDestination?.route?.let { to ->
                SessionReplay.setTakeFullSnapshot(true)
                val attributes = mapOf("event_type" to "navigation")

                AnalyticsControllerImpl.getInstance().recordBreadcrumb("screen_name: $to", attributes)

                if (FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing) && to != lastMobileViewRoute) {
                    lastMobileViewRoute = to
                    try {
                        MobileViewContext.getInstance().onViewAppeared(to, null, null)
                    } catch (e: Exception) {
                        log.error("ComposeNavigationInstrumentation.destinationChangedListener: ", e)
                    }
                }
            }
        }
}
