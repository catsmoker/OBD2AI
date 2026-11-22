package com.catsmoker.obd2ai.ui.connect

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.common.ConnectionStateStore
import com.catsmoker.obd2ai.ui.home.sectionOptions

/**
 * Connect destination: ports the old fragment's non-UI wiring — the
 * Bluetooth-enable activity result, the resume-time enable/refresh, device
 * discovery refresh, and the post-connect navigation (bar mode lands on the
 * dashboard, cards mode and cancellations return to Home).
 */
@Composable
fun ConnectDestination(activity: MainActivity, navController: NavHostController) {
    val context = LocalContext.current
    val bluetoothHelper = activity.bluetoothHelper
    val obdHelper = activity.obdHelper
    val viewModel: ConnectViewModel = hiltViewModel()
    val uiState = viewModel.uiState

val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshDevices()
        } else {
            Toast.makeText(context, R.string.permissions_bluetooth_required, Toast.LENGTH_LONG)
                .show()
        }
    }

    fun navigateAfterConnect() {
        ConnectionStateStore.refresh(obdHelper)
        // Home is the hub now: a successful connect returns there, where the
        // feature tiles (and the live dashboard) are one tap away.
        navController.popBackStack("home", inclusive = false)
    }

    // Resume behavior from the old fragment: prompt to enable Bluetooth or
    // (re)start discovery.
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!bluetoothHelper.isBluetoothEnabled()) {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } else {
                    viewModel.refreshDevices()
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            try {
                bluetoothHelper.stopDiscovery()
            } catch (_: Exception) {
            }
        }
    }

    ConnectScreen(
        uiState = uiState,
        bluetoothHelper = bluetoothHelper,
        obdHelper = obdHelper,
        onEnableBluetooth = {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        },
        onRefresh = { viewModel.refreshDevices() },
        onStopScan = { viewModel.stopScan() },
        navigateToHome = { navigateAfterConnect() },
        onBack = { navController.navigateUp() }
    )
}
