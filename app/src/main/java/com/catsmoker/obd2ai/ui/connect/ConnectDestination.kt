package com.catsmoker.obd2ai.ui.connect

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.common.ConnectionStateStore
import com.catsmoker.obd2ai.ui.home.sectionOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connect destination: ports the old fragment's non-UI wiring — the
 * Bluetooth-enable activity result, the resume-time enable/refresh, device
 * discovery refresh, and the post-connect navigation (bar mode lands on the
 * dashboard, cards mode and cancellations return to Home).
 */
@Composable
fun ConnectDestination(activity: MainActivity, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bluetoothHelper = activity.bluetoothHelper
    val obdHelper = activity.obdHelper
    val uiState = remember { ConnectUiState() }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun refreshDevices() {
        if (!bluetoothHelper.checkBluetoothPermissions()) {
            Toast.makeText(context, R.string.permissions_bluetooth_required, Toast.LENGTH_SHORT)
                .show()
            uiState.isRefreshing.value = false
            return
        }
        try {
            val pairedDevices = bluetoothHelper.getPairedDevices()
            uiState.pairedDevices.value = pairedDevices
            uiState.selectedDevice.value = null
            bluetoothHelper.startDiscovery()
            uiState.isRefreshing.value = true
            scope.launch {
                delay(12000)
                try {
                    bluetoothHelper.stopDiscovery()
                } catch (_: Exception) {
                }
                uiState.isRefreshing.value = false
            }
        } catch (e: Exception) {
            uiState.isRefreshing.value = false
            Toast.makeText(
                context,
                context.getString(R.string.connect_no_devices_found, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshDevices()
        } else {
            Toast.makeText(context, R.string.permissions_bluetooth_required, Toast.LENGTH_LONG)
                .show()
        }
    }

    fun navigateAfterConnect() {
        ConnectionStateStore.refresh(obdHelper)
        if (obdHelper.isConnected && !ConnectionStateStore.cardsMode.value) {
            // Bar mode: a successful connect lands in the app (dashboard)
            // instead of bouncing back to the Home menu.
            navController.navigate("live") { sectionOptions() }
        } else {
            // Cards mode or a cancelled attempt: back to Home.
            navController.popBackStack("home", inclusive = false)
        }
    }

    // Resume behavior from the old fragment: prompt to enable Bluetooth or
    // (re)start discovery.
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!bluetoothHelper.isBluetoothEnabled()) {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } else {
                    refreshDevices()
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
        onRefresh = { refreshDevices() },
        navigateToHome = { navigateAfterConnect() }
    )
}
