package com.catsmoker.obd2ai.ui.trip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units

/**
 * Trip destination for the single-activity nav graph. Units are read once
 * per entry (the old fragment read them at view creation too); the screen
 * itself is [TripScreen].
 */
@Composable
fun TripDestination(activity: MainActivity, navController: NavHostController) {
    val prefs = LocalContext.current.getSharedPreferences(PrefsKeys.PREFS_NAME, 0)
    val imperial = remember { Units.isImperial(prefs) }
    TripScreen(
        imperial = imperial,
        onBack = { navController.navigateUp() },
        // Demo writes the flows itself; a real adapter only polls on Live.
        demoMode = remember { activity.obdHelper.demoMode }
    )
}
