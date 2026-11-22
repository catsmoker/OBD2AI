package com.catsmoker.obd2ai.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.ui.trip.TripSample
import com.catsmoker.obd2ai.ui.trip.TripSession
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.common.UiSettings
import com.catsmoker.obd2ai.ui.connect.ConnectDestination
import com.catsmoker.obd2ai.ui.console.ConsoleDestination
import com.catsmoker.obd2ai.ui.dashboard.LiveDestination
import com.catsmoker.obd2ai.ui.diagnostics.AskAiDestination
import com.catsmoker.obd2ai.ui.diagnostics.ErrorDetailDestination
import com.catsmoker.obd2ai.ui.diagnostics.ErrorOverviewDestination
import com.catsmoker.obd2ai.ui.glass.AppBackdropHost
import com.catsmoker.obd2ai.ui.home.HomeDestination
import com.catsmoker.obd2ai.ui.onboarding.PermissionsDestination
import com.catsmoker.obd2ai.ui.settings.AboutDestination
import com.catsmoker.obd2ai.ui.settings.LegalDestination
import com.catsmoker.obd2ai.ui.settings.SettingsDestination
import com.catsmoker.obd2ai.ui.setup.SetupDestination
import com.catsmoker.obd2ai.ui.theme.LocalReduceMotion
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.catsmoker.obd2ai.ui.theme.ObdMotion
import com.catsmoker.obd2ai.ui.theme.rememberReduceMotion
import com.catsmoker.obd2ai.ui.trip.TripDestination

/**
 * Single-activity Compose root: owns the theme, the navigation graph, the
 * ambient/backdrop layer and the global ad banner. Screens are composables;
 * fragments and the XML navigation graph are gone.
 *
 * There is no section chrome: navigation is hub-and-spoke from the Home root,
 * with system Back to leave a screen. The backdrop host is what gives the
 * glass surfaces something to refract.
 */
@Composable
fun AppRoot(activity: MainActivity, startOnSetup: Boolean) {
    val darkTheme = isSystemInDarkTheme()
    val prefs = remember {
        activity.getSharedPreferences(PrefsKeys.PREFS_NAME, 0)
    }
    remember { UiSettings.seed(prefs); true }
    val dynamicColor by UiSettings.dynamicColor.collectAsStateWithLifecycle()

    Obd2AiTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion()) {
            AppBackdropHost(background = MaterialTheme.colorScheme.background) {
                AppRootContent(activity, startOnSetup)
            }
        }
    }
}

@Composable
private fun AppRootContent(activity: MainActivity, startOnSetup: Boolean) {
    val navController = rememberNavController()
    val reduceMotion = LocalReduceMotion.current

    // Edge-to-edge: pad once for system bars + cutouts, mirroring the old
    // applySystemBarInsets listener. IME behavior stays with the window.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
    ) {
        TripSampler(activity)
        AppNavHost(
            navController = navController,
            activity = activity,
            startOnSetup = startOnSetup,
            reduceMotion = reduceMotion,
            modifier = Modifier.weight(1f)
        )
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

/**
 * One app-wide trip timer. It lives here (not in `TripScreen`) so a trip keeps
 * counting while the driver looks at another screen, and it only runs while
 * the app is resumed — a backgrounded timer would integrate stale speed into
 * invented distance.
 *
 * The same reasoning applies while resumed: the flows hold the last reading
 * even after polling stops, so only a live monitor may feed the trip. Without
 * it the trip counts time and idle time, never distance it did not observe.
 */
@Composable
private fun TripSampler(activity: MainActivity) {
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                val allowed = TripSample.dataAllowed(
                    monitoring = ObdDataHolder.isMonitoring.get(),
                    demo = activity.obdHelper.demoMode
                )
                TripSession.sample(
                    nowMs = System.currentTimeMillis(),
                    speedText = TripSample.speedText(allowed, ObdDataHolder.speedFlow.value),
                    rpmText = TripSample.rpmText(allowed, ObdDataHolder.rpmFlow.value)
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    activity: MainActivity,
    startOnSetup: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    // Expressive but quiet: content fades and settles a few pixels. With
    // reduced motion the screens simply swap.
    val duration = if (reduceMotion) 0 else ObdMotion.NAV_FADE_MS
    val enter = fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = 0.985f)
    val exit = fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = 0.99f)

    NavHost(
        navController = navController,
        startDestination = if (startOnSetup) "setup" else "home",
        modifier = modifier.fillMaxSize(),
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit }
    ) {
        composable("home") { HomeDestination(activity, navController) }
        composable("setup") { SetupDestination(activity, navController) }
        composable("permissions") { PermissionsDestination(navController) }
        composable("connect") { ConnectDestination(activity, navController) }
        composable("live") { LiveDestination(activity, navController) }
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
        composable("trip") { TripDestination(activity, navController) }
        composable("console") { ConsoleDestination(activity, navController) }
        composable("settings") { SettingsDestination(activity, navController) }
        composable("about") { AboutDestination(navController) }
        composable(
            "legal/{page}",
            arguments = listOf(navArgument("page") { type = NavType.StringType })
        ) { entry ->
            LegalDestination(
                page = entry.arguments?.getString("page") ?: "privacy",
                onBack = { navController.navigateUp() }
            )
        }
    }
}
