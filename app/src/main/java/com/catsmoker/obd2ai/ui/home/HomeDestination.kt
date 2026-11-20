package com.catsmoker.obd2ai.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.common.AppNav
import com.catsmoker.obd2ai.ui.common.ConnectionState
import com.catsmoker.obd2ai.ui.common.ConnectionStateStore

/**
 * Home destination: the connection card plus (cards mode only) the feature
 * tile grid. Ports the old OnboardingFragment: demo start, disconnect, and
 * the connect-gated drill-downs. Chrome visibility and the connection state
 * come from [ConnectionStateStore], refreshed on every resume so back
 * navigation from connect/live always shows the truth.
 */
@Composable
fun HomeDestination(activity: MainActivity, navController: NavHostController) {
    val state by ConnectionStateStore.state.collectAsStateWithLifecycle()
    val cardsMode by ConnectionStateStore.cardsMode.collectAsStateWithLifecycle()

    fun refresh() = ConnectionStateStore.refresh(activity.obdHelper)

    // The old fragment refreshed in onResume (back from connect, dashboard
    // teardown, ...). Same behavior via the lifecycle observer.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // First composition: the store may still hold a stale value from before
    // a process-death restore.
    LaunchedEffect(Unit) { refresh() }

    /** Data screens read from the adapter: detour via connect first. */
    fun openConnected(route: String) {
        if (activity.obdHelper.isConnected) {
            navController.navigate(route) { sectionOptions() }
        } else {
            navController.navigate("connect") { sectionOptions() }
        }
    }

    HomeScreen(
        state = state,
        onConnect = {
            val prefs = activity.getSharedPreferences(PrefsKeys.PREFS_NAME, 0)
            val speedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)
            if (speedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
                // GPS mode needs no adapter: straight to the dashboard.
                navController.navigate("live") { sectionOptions() }
            } else if (activity.bluetoothHelper.checkBluetoothPermissions()) {
                navController.navigate("connect") { sectionOptions() }
            } else {
                navController.navigate("permissions") { sectionOptions() }
            }
        },
        onTryDemo = {
            activity.obdHelper.setupDemo()
            refresh()
            if (!cardsMode) {
                // Bar mode: no Home menu — go straight into the app.
                navController.navigate("live") { sectionOptions() }
            }
        },
        onDisconnect = {
            activity.obdHelper.disconnectAll()
            refresh()
        },
        onOpenDashboard = { openConnected("live") },
        onOpenDiagnostics = { openConnected("diagnostics") },
        onOpenTrip = { navController.navigate("trip") { sectionOptions() } },
        onOpenConsole = { navController.navigate("console") { sectionOptions() } },
        onOpenSettings = { navController.navigate("settings") { sectionOptions() } },
        onOpenAbout = { navController.navigate("about") { sectionOptions() } },
        // Bar mode: the dock owns top-level navigation, so the tile grid
        // would duplicate it — Home is the connect screen only. Cards mode
        // keeps the tiles.
        showTiles = cardsMode && state != ConnectionState.DISCONNECTED
    )
}

/** Dock-style navigation: single-top, back stack collapsed to the root. */
fun NavOptionsBuilder.sectionOptions() {
    launchSingleTop = true
    popUpTo("home") { inclusive = false }
}
