package com.catsmoker.obd2ai.ui.connect

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.BluetoothHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder for the connect screen: paired-device discovery and the
 * refresh cycle live here so they survive recomposition and are testable
 * independently of the Compose UI. The destination keeps only the
 * activity-result launcher (Bluetooth enable) and navigation.
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val bluetoothHelper: BluetoothHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val uiState = ConnectUiState()

    /**
     * Refreshes the paired-device list and starts discovery, exactly like
     * the old fragment: permission check first (honest failure, no fake
     * empty list), then discovery with a fixed 12 s cutoff.
     */
    fun refreshDevices() {
        // A fresh attempt supersedes whatever the last one said.
        uiState.scanError.value = null
        if (!bluetoothHelper.checkBluetoothPermissions()) {
            Toast.makeText(
                appContext,
                R.string.permissions_bluetooth_required,
                Toast.LENGTH_SHORT
            ).show()
            uiState.isRefreshing.value = false
            return
        }
        try {
            val pairedDevices = bluetoothHelper.getPairedDevices()
            uiState.pairedDevices.value = pairedDevices
            uiState.selectedDevice.value = null
            bluetoothHelper.startDiscovery()
            uiState.isRefreshing.value = true
            viewModelScope.launch {
                delay(12000)
                try {
                    bluetoothHelper.stopDiscovery()
                } catch (_: Exception) {
                }
                uiState.isRefreshing.value = false
            }
        } catch (e: Exception) {
            uiState.isRefreshing.value = false
            // Sticky detail in the empty state beats a toast nobody catches.
            uiState.scanError.value = appContext.getString(
                R.string.connect_no_devices_found,
                e.message ?: appContext.getString(R.string.connect_error_unknown)
            )
        }
    }

    /** Ends the scan window early; the list keeps whatever was found so far. */
    fun stopScan() {
        try {
            bluetoothHelper.stopDiscovery()
        } catch (_: Exception) {
        }
        uiState.isRefreshing.value = false
    }

    override fun onCleared() {
        try {
            bluetoothHelper.stopDiscovery()
        } catch (_: Exception) {
        }
    }
}
