package com.catsmoker.obd2ai.ui.connect

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.BluetoothDeviceDTO
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.common.AppNav
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import kotlinx.coroutines.launch

/**
 * Connection screen: scan/pair Bluetooth devices, connect (SPP) or WiFi,
 * plus demo mode. Keeps the XML behavior exactly — discovery broadcast
 * receiver, Bluetooth-enable prompt, paired-device list, connect/demo/WiFi
 * actions — with Compose as the view layer.
 *
 * [ConnectUiState] is the single shared state: the fragment writes to it on
 * resume/scan, Compose reads it (and recomposes on change). The fragment
 * owns the activity-result launcher; Compose owns the discovery receiver
 * (registered/unregistered via DisposableEffect).
 */
class ConnectFragment : Fragment() {

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var obdHelper: ObdHelper
    private val uiState = ConnectUiState()

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshDevices()
        } else {
            Toast.makeText(
                context, R.string.permissions_bluetooth_required, Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bluetoothHelper = (activity as MainActivity).bluetoothHelper
        obdHelper = (activity as MainActivity).obdHelper

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
                    ConnectScreen(
                        uiState = uiState,
                        bluetoothHelper = bluetoothHelper,
                        obdHelper = obdHelper,
                        onEnableBluetooth = {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(intent)
                        },
                        onRefresh = ::refreshDevices,
                        navigateToHome = {
                            // Bar mode: a successful connect lands in the app
                            // (dashboard) instead of bouncing back to the Home
                            // menu; a cancelled attempt still returns to Home.
                            if (obdHelper.isConnected &&
                                !AppNav.isCardsMode(navStyleTag())
                            ) {
                                findNavController().navigate(
                                    R.id.action_connectFragment_to_liveDataFragment
                                )
                            } else {
                                findNavController().navigate(
                                    R.id.action_connectFragment_to_onboardingFragment
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    private fun navStyleTag(): String? =
        activity?.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(PrefsKeys.NAV_STYLE, null)

    override fun onResume() {
        super.onResume()
        if (!bluetoothHelper.isBluetoothEnabled()) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        } else {
            refreshDevices()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun refreshDevices() {
        if (!bluetoothHelper.checkBluetoothPermissions()) {
            Toast.makeText(
                context, R.string.permissions_bluetooth_required, Toast.LENGTH_SHORT
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
            Handler(Looper.getMainLooper()).postDelayed({
                try { bluetoothHelper.stopDiscovery() } catch (_: Exception) {}
                uiState.isRefreshing.value = false
            }, 12000)
        } catch (e: Exception) {
            uiState.isRefreshing.value = false
            Toast.makeText(
                context,
                requireActivity().getString(R.string.connect_no_devices_found, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { bluetoothHelper.stopDiscovery() } catch (e: SecurityException) {
            Log.w("ConnectFragment", "Stop discovery without permission", e)
        }
    }
}

/** Single shared UI state for the connect screen. Fragment writes, Compose reads. */
class ConnectUiState {
    val pairedDevices = mutableStateOf(emptyList<BluetoothDeviceDTO>())
    val selectedDevice = mutableStateOf<BluetoothDeviceDTO?>(null)
    val isRefreshing = mutableStateOf(false)
    val isConnecting = mutableStateOf(false)
    val showWifiDialog = mutableStateOf(false)
    val wifiHost = mutableStateOf("")
    val wifiPort = mutableStateOf("")
}

/** Compose content for the connect screen. */
@Composable
fun ConnectScreen(
    uiState: ConnectUiState,
    bluetoothHelper: BluetoothHelper,
    obdHelper: ObdHelper,
    onEnableBluetooth: () -> Unit,
    onRefresh: () -> Unit,
    navigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val devices by uiState.pairedDevices
    val selectedDevice by uiState.selectedDevice
    val isRefreshing by uiState.isRefreshing
    val isConnecting by uiState.isConnecting

    var wifiDialogOpen by remember { mutableStateOf(uiState.showWifiDialog.value) }
    var wifiHost by remember { mutableStateOf(uiState.wifiHost.value) }
    var wifiPort by remember { mutableStateOf(uiState.wifiPort.value) }

    // Discovery receiver, tied to composition lifecycle.
    val filter = IntentFilter("android.bluetooth.device.action.FOUND")
    filter.addAction("android.bluetooth.adapter.action.DISCOVERY_FINISHED")
    val receiver = remember {
        object : BroadcastReceiver() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onReceive(context: Context, intent: Intent) {
                if (!bluetoothHelper.checkBluetoothPermissions()) return
                when (intent.action) {
                    "android.bluetooth.device.action.FOUND" -> {
                        val device: BluetoothDevice? =
                            IntentCompat.getParcelableExtra(
                                intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                            )
                        device?.takeIf { it.name != null }?.let {
                            uiState.pairedDevices.value =
                                uiState.pairedDevices.value + bluetoothHelper.convertToDeviceDTO(it)
                        }
                    }
                    "android.bluetooth.adapter.action.DISCOVERY_FINISHED" -> {
                        uiState.isRefreshing.value = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    DisposableEffect(receiver) {
        onDispose {
            try { (context as? Activity)?.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
            try { bluetoothHelper.stopDiscovery() } catch (_: Exception) {}
        }
    }

    // Keep dialog-local state in sync with shared state when it re-opens.
    LaunchedEffect(uiState.showWifiDialog.value) {
        wifiDialogOpen = uiState.showWifiDialog.value
        if (wifiDialogOpen) {
            wifiHost = uiState.wifiHost.value
            wifiPort = uiState.wifiPort.value
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.connect_select_device),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.connect_scanning_for_devices),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = devices,
                key = { it.address }
            ) { device ->
                DeviceRow(
                    device = device,
                    selected = selectedDevice?.address == device.address,
                    onClick = { uiState.selectedDevice.value = device }
                )
            }
        }

        if (devices.isEmpty() && !isRefreshing) {
            Text(
                text = stringResource(R.string.connect_no_devices_found, "no devices"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        selectedDevice?.let {
            Text(
                text = stringResource(R.string.connected_to, it.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Rescan action.
        androidx.compose.material3.Button(
            onClick = { onRefresh() },
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isRefreshing) stringResource(R.string.connect_scanning_for_devices)
                else stringResource(R.string.connect_button)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Connect to the selected device.
        ObdButton(
            onClick = {
                scope.launch {
                    if (!bluetoothHelper.checkBluetoothPermissions()) {
                        Toast.makeText(
                            context, R.string.permissions_bluetooth_required, Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    val device = selectedDevice ?: return@launch
                    uiState.isConnecting.value = true
                    try {
                        Toast.makeText(
                            context, R.string.connect_scanning_for_devices, Toast.LENGTH_LONG
                        ).show()
                        obdHelper.setupObd(device.address)
                        obdHelper.initializeObd()
                        navigateToHome()
                    } catch (e: SecurityException) {
                        Log.e("ConnectFragment", "Bluetooth permission revoked", e)
                        Toast.makeText(
                            context, R.string.permissions_bluetooth_required, Toast.LENGTH_LONG
                        ).show()
                        uiState.isConnecting.value = false
                    } catch (e: Exception) {
                        Log.e("ConnectFragment", "Connection failed", e)
                        Toast.makeText(
                            context,
                            context.getString(R.string.could_not_connect_to_obd2_adapter, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                        uiState.isConnecting.value = false
                    }
                }
            },
            enabled = selectedDevice != null && !isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isConnecting) stringResource(R.string.connect_button) + "…"
                else stringResource(R.string.connect_button)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        ObdOutlinedButton(
            onClick = {
                uiState.showWifiDialog.value = true
                wifiDialogOpen = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            RowWithIconLabel(label = stringResource(R.string.connect_wifi_button))
        }

        Spacer(modifier = Modifier.height(8.dp))

        ObdOutlinedButton(
            onClick = {
                obdHelper.setupDemo()
                navigateToHome()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            RowWithIconLabel(label = stringResource(R.string.connect_demo_button))
        }
    }

    // Wi-Fi dialog
    if (wifiDialogOpen) {
        AlertDialog(
            title = { Text(text = stringResource(R.string.connect_wifi_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = wifiHost,
                        onValueChange = { wifiHost = it },
                        label = { Text(stringResource(R.string.connect_wifi_host_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = wifiPort,
                        onValueChange = { wifiPort = it },
                        label = { Text(stringResource(R.string.connect_wifi_port_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val host = wifiHost.trim()
                    val port = wifiPort.toIntOrNull()
                    if (host.isEmpty() || port == null || port !in 1..65535) {
                        Toast.makeText(context, R.string.connect_wifi_invalid, Toast.LENGTH_LONG).show()
                        return@TextButton
                    }
                    wifiDialogOpen = false
                    uiState.showWifiDialog.value = false
                    uiState.wifiHost.value = host
                    uiState.wifiPort.value = port.toString()
                    scope.launch {
                        try {
                            Toast.makeText(
                                context, R.string.connect_scanning_for_devices, Toast.LENGTH_LONG
                            ).show()
                            obdHelper.setupWifi(host, port)
                            obdHelper.initializeObd()
                            navigateToHome()
                        } catch (e: Exception) {
                            Log.e("ConnectFragment", "WiFi connection failed", e)
                            Toast.makeText(
                                context,
                                context.getString(R.string.could_not_connect_to_obd2_adapter, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) {
                    Text(text = stringResource(R.string.connect_wifi_button))
                }
            },
            onDismissRequest = { wifiDialogOpen = false }
        )
    }
}

/** Single selectable device row. */
@Composable
private fun DeviceRow(
    device: BluetoothDeviceDTO,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = device.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = device.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Centered label row for the two outlined buttons. */
@Composable
private fun RowWithIconLabel(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
