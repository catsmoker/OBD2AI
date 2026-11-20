package com.catsmoker.obd2ai.ui.console

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.catsmoker.obd2ai.MainActivity

/** Console destination for the single-activity nav graph. */
@Composable
fun ConsoleDestination(activity: MainActivity, navController: NavHostController) {
    ConsoleScreen(
        obdHelper = activity.obdHelper,
        onBack = { navController.navigateUp() }
    )
}
