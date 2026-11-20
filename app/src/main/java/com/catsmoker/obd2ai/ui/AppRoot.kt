package com.catsmoker.obd2ai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.common.AppNav
import com.catsmoker.obd2ai.ui.common.ConnectionState
import com.catsmoker.obd2ai.ui.common.ConnectionStateStore
import com.catsmoker.obd2ai.ui.connect.ConnectDestination
import com.catsmoker.obd2ai.ui.console.ConsoleDestination
import com.catsmoker.obd2ai.ui.diagnostics.AskAiDestination
import com.catsmoker.obd2ai.ui.diagnostics.ErrorDetailDestination
import com.catsmoker.obd2ai.ui.diagnostics.ErrorOverviewDestination
import com.catsmoker.obd2ai.ui.dashboard.LiveDestination
import com.catsmoker.obd2ai.ui.home.HomeDestination
import com.catsmoker.obd2ai.ui.onboarding.PermissionsDestination
import com.catsmoker.obd2ai.ui.settings.AboutDestination
import com.catsmoker.obd2ai.ui.settings.LegalDestination
import com.catsmoker.obd2ai.ui.settings.SettingsDestination
import com.catsmoker.obd2ai.ui.setup.SetupDestination
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.catsmoker.obd2ai.ui.trip.TripDestination

/**
 * Single-activity Compose root: owns the navigation graph, the chrome
 * (phone bottom dock / tablet rail) and the global ad banner. Screens are
 * composables; fragments and the XML navigation graph are gone.
 *
 * Chrome visibility follows [AppNav]: hidden while disconnected (every tab
 * needs a connection), on the transient setup screens, and always in cards
 * mode. The dock's tabs are Console / Live / Diagnostics / Trip — the Home
 * root sits above the tabs (reachable via system back).
 */
@Composable
fun AppRoot(activity: MainActivity, startOnSetup: Boolean) {
    Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
        AppRootContent(activity, startOnSetup)
    }
}

@Composable
private fun AppRootContent(activity: MainActivity, startOnSetup: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: "home"
    val connectionState by ConnectionStateStore.state.collectAsStateWithLifecycle()
    val cardsMode by ConnectionStateStore.cardsMode.collectAsStateWithLifecycle()
    val linked = connectionState != ConnectionState.DISCONNECTED
    val chromeHidden = AppNav.isChromeHidden(route, linked, cardsMode)
    val section = AppNav.sectionFor(route)
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val haptic = LocalHapticFeedback.current

    /** Collapse the back stack to a known destination; reuse when there. */
    fun goSection(target: String) {
        if (route == target) return
        navController.navigate(target) {
            launchSingleTop = true
            popUpTo("home") { inclusive = false }
        }
    }

    fun tabTap(target: String) {
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        goSection(target)
    }

    // Edge-to-edge: pad once for system bars + cutouts, mirroring the old
    // applySystemBarInsets listener. IME behavior stays with the window.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
    ) {
        if (isTablet) {
            // Tablet: rail + content side by side, banner below both.
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (!chromeHidden) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            if (AppNav.showBackArrow(route)) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    navController.navigateUp()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.nav_back_desc),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    ) {
                        RailItems(section) { tabTap(it) }
                    }
                }
                AppNavHost(navController, activity, startOnSetup, Modifier.weight(1f))
            }
        } else {
            // Phone: content on top, dock above the banner.
            AppNavHost(navController, activity, startOnSetup, Modifier.weight(1f))
            if (!chromeHidden) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TabItems(section) { tabTap(it) }
                }
            }
        }
        AdBanner(activity)
    }
}

/**
 * The global ad banner, hosted with AndroidView so the AdMob view keeps its
 * lifecycle (pause/resume/destroy follow the Activity). [MainActivity]
 * owns the instance; composition just mounts it and triggers the load gate.
 */
@Composable
private fun AdBanner(activity: MainActivity) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { activity.createAdView() }
    )
    androidx.compose.runtime.LaunchedEffect(Unit) {
        activity.applyAdChoice()
    }
}

/** Tab data shared by the phone dock and the tablet rail. */
private data class TabSpec(val route: String, val icon: Int, val label: Int)

private val TABS = listOf(
    TabSpec("console", R.drawable.ic_terminal, R.string.obd_console_title),
    TabSpec("live", R.drawable.ic_gauge, R.string.dock_live),
    TabSpec("diagnostics", R.drawable.ic_engine, R.string.dock_faults),
    TabSpec("trip", R.drawable.ic_route, R.string.dock_trip)
)

/** Phone dock items. NavigationBar's content scope is RowScope. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItems(
    section: String?,
    onTap: (String) -> Unit
) {
    TABS.forEach { tab ->
        NavigationBarItem(
            selected = section == tab.route,
            onClick = { onTap(tab.route) },
            icon = {
                Icon(
                    painter = painterResource(tab.icon),
                    contentDescription = stringResource(tab.label),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            label = { Text(stringResource(tab.label)) }
        )
    }
}

/** Tablet rail items. NavigationRail's content scope is ColumnScope. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.RailItems(
    section: String?,
    onTap: (String) -> Unit
) {
    TABS.forEach { tab ->
        NavigationRailItem(
            selected = section == tab.route,
            onClick = { onTap(tab.route) },
            icon = {
                Icon(
                    painter = painterResource(tab.icon),
                    contentDescription = stringResource(tab.label),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            label = { Text(stringResource(tab.label)) }
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    activity: MainActivity,
    startOnSetup: Boolean,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = if (startOnSetup) "setup" else "home",
        modifier = modifier.fillMaxSize()
    ) {
        composable("home") { HomeDestination(activity, navController) }
        composable("setup") { SetupDestination(activity, navController) }
        composable("permissions") { PermissionsDestination(navController) }
        composable("connect") { ConnectDestination(activity, navController) }
        composable("live") { LiveDestination(activity) }
        composable("diagnostics") { ErrorOverviewDestination(activity, navController) }
        composable(
            "errorDetail/{errorCode}",
            arguments = listOf(navArgument("errorCode") { type = NavType.StringType })
        ) { entry ->
            ErrorDetailDestination(
                activity,
                navController,
                entry.arguments?.getString("errorCode").orEmpty()
            )
        }
        composable(
            "askAi/{errorCode}",
            arguments = listOf(navArgument("errorCode") { type = NavType.StringType })
        ) { entry ->
            AskAiDestination(activity, navController, entry.arguments?.getString("errorCode").orEmpty())
        }
        composable("trip") { TripDestination(navController) }
        composable("console") { ConsoleDestination(activity, navController) }
        composable("settings") { SettingsDestination(activity, navController) }
        composable("about") { AboutDestination(navController) }
        composable(
            "legal/{page}",
            arguments = listOf(navArgument("page") { type = NavType.StringType })
        ) { entry ->
            LegalDestination(entry.arguments?.getString("page") ?: "privacy")
        }
    }
}
