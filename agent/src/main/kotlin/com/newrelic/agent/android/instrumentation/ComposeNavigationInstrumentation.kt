package com.newrelic.agent.android.instrumentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl
import com.newrelic.agent.android.sessionReplay.SessionReplay
import  kotlin.collections.Map
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

    private val destinationChangedListener =
        NavController.OnDestinationChangedListener { controller, _, _ ->
            controller.currentDestination?.route?.let { to ->
                SessionReplay.setTakeFullSnapshot(true)
                val attributes = mapOf("event_type" to "navigation")

                AnalyticsControllerImpl.getInstance().recordBreadcrumb("screen_name: $to", attributes)
            }
        }
}
