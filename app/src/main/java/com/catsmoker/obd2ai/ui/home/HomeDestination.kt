package com.catsmoker.obd2ai.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.common.DemoSession
import com.catsmoker.obd2ai.ui.common.ConnectionState
import com.catsmoker.obd2ai.ui.common.ConnectionStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home destination: the connection card plus the feature tile grid — the
 * hub-and-spoke root of the app now that section chrome is gone. Ports the
 * old OnboardingFragment: demo start, disconnect, and the connect-gated
 * drill-downs. The connection state comes from [ConnectionStateStore],
 * refreshed on every resume so back navigation from connect/live always
 * shows the truth.
 */
@Composable
fun HomeDestination(activity: MainActivity, navController: NavHostController) {
    val state by ConnectionStateStore.state.collectAsStateWithLifecycle()
    val lossReason by ConnectionStateStore.lossReason.collectAsStateWithLifecycle()

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

    // A saved scan is reviewable offline, so Home needs to know it exists.
    // Re-read whenever the screen comes back (a scan or a clear may have
    // happened on another screen).
    val context = LocalContext.current
    var hasSavedScan by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        hasSavedScan = withContext(Dispatchers.IO) {
            DtcStore.loadScan(context).codes.isNotEmpty()
        }
    }

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
            ConnectionStateStore.clearLoss()
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
            DemoSession.remember(activity)
            refresh()
        },
        onDisconnect = {
            activity.obdHelper.disconnectAll()
            DemoSession.forget(activity)
            // A deliberate exit is not a failure: wipe any earlier drop note.
            ConnectionStateStore.clearLoss()
            refresh()
        },
        onOpenDashboard = { openConnected("live") },
        onOpenDiagnostics = { openConnected("diagnostics") },
        // The saved-scan tile is a document: no connection required.
        onOpenSavedDiagnostics = { navController.navigate("diagnostics") { sectionOptions() } },
        onOpenTrip = { navController.navigate("trip") { sectionOptions() } },
        onOpenConsole = { navController.navigate("console") { sectionOptions() } },
        onOpenSettings = { navController.navigate("settings") { sectionOptions() } },
        onOpenAbout = { navController.navigate("about") { sectionOptions() } },
        // The tile grid is the only top-level navigation left, so it shows
        // whenever the app is linked; disconnected Home is the connection
        // card only.
        showTiles = state != ConnectionState.DISCONNECTED,
        // A drop explains itself; a deliberate disconnect leaves no note
        // because refresh()/clearLoss() wipe the reason.
        lossReason = ConnectionState.visibleLossReason(state, lossReason),
        hasSavedScan = hasSavedScan
    )
}

/** Dock-style navigation: single-top, back stack collapsed to the root. */
fun NavOptionsBuilder.sectionOptions() {
    launchSingleTop = true
    popUpTo("home") { inclusive = false }
}
