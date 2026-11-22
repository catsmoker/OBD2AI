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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.BluetoothDeviceDTO
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.common.DemoSession
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
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
class ConnectUiState {
    val pairedDevices = mutableStateOf(emptyList<BluetoothDeviceDTO>())
    val selectedDevice = mutableStateOf<BluetoothDeviceDTO?>(null)
    val isRefreshing = mutableStateOf(false)
    val isConnecting = mutableStateOf(false)
    /** Why the last scan produced nothing, shown in the empty state. */
    val scanError = mutableStateOf<String?>(null)
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
    onStopScan: () -> Unit = {},
    navigateToHome: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val devices by uiState.pairedDevices
    val selectedDevice by uiState.selectedDevice
    val isRefreshing by uiState.isRefreshing
    val isConnecting by uiState.isConnecting

    // A failed attempt is sticky and readable: a toast is gone before you can
    // act on it, and "Could not connect" without the reason is not a diagnosis.
    var connectError by rememberSaveable { mutableStateOf<String?>(null) }
    // Resolved here because the connect attempts run in a coroutine, not in
    // composition. Keyed on the locale so a language change re-resolves it.
    val unknownDetail = stringResource(R.string.connect_error_unknown)
    // Which adapter the attempt is talking to, so the wait is a named wait and
    // a Wi-Fi attempt no longer claims to be "scanning for devices".
    var connectingLabel by rememberSaveable { mutableStateOf<String?>(null) }
    // Read once per visit: advisory hint, never a preference that hides options.
    val lastTransport = remember { TransportMemory.last(context) }
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

    // The subtitle carries the truth about discovery: a hardcoded
    // "Scanning…" line kept claiming to scan long after it stopped, and
    // contradicted the button next to it.
    val statusLine = when {
        isRefreshing -> stringResource(R.string.connect_scanning_for_devices)
        devices.isNotEmpty() -> stringResource(R.string.connect_devices_found, devices.size)
        else -> stringResource(R.string.connect_select_device)
    }

    ScreenScaffold(
        title = stringResource(R.string.connect_title),
        subtitle = statusLine,
        onBack = onBack,
        modifier = modifier.fillMaxSize()
    ) {
        lastTransport?.let { used ->
            Row(
                modifier = Modifier.padding(bottom = ObdSpacing.CardGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.connect_last_transport,
                        stringResource(
                            if (used == ObdTransport.WIFI) R.string.connect_transport_wifi
                            else R.string.connect_transport_bluetooth
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(ObdSpacing.CardGap)
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
            if (devices.isEmpty() && !isRefreshing) {
                item {
                    // Informational only: the screen-level Rescan button below
                    // already owns that action, and two identical buttons in
                    // one viewport is one too many.
                    EmptyAdaptersCard(scanError = uiState.scanError.value)
                }
            }
        }

        connectError?.let { detail ->
            Spacer(modifier = Modifier.height(ObdSpacing.CardGap))
            ConnectFailureCard(detail = detail)
        }

        if (isConnecting && connectingLabel != null) {
            Spacer(modifier = Modifier.height(ObdSpacing.CardGap))
            ConnectingCard(target = connectingLabel!!)
        }

        Spacer(modifier = Modifier.height(ObdSpacing.CardGap))

        // Primary action: connect to the highlighted adapter.
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
                    connectError = null
                    connectingLabel = device.name.takeIf { it.isNotBlank() } ?: device.address
                    uiState.isConnecting.value = true
                    try {
                        obdHelper.setupObd(device.address)
                        obdHelper.initializeObd()
                        TransportMemory.remember(context, ObdTransport.BLUETOOTH)
                        navigateToHome()
                    } catch (e: SecurityException) {
                        Log.e("ConnectScreen", "Bluetooth permission revoked", e)
                        connectError = context.getString(R.string.permissions_bluetooth_required)
                    } catch (e: Exception) {
                        Log.e("ConnectScreen", "Connection failed", e)
                        connectError = failureDetail(e, unknownDetail)
                    } finally {
                        uiState.isConnecting.value = false
                        connectingLabel = null
                    }
                }
            },
            enabled = selectedDevice != null && !isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isConnecting) stringResource(R.string.connect_connecting)
                else stringResource(R.string.connect_button)
            )
        }

        Spacer(modifier = Modifier.height(ObdSpacing.CardGap))

        // While a scan runs this button stops it: the screen otherwise sits on
        // "Scanning…" for the full 12 s window with no way out.
        ObdOutlinedButton(
            onClick = { if (isRefreshing) onStopScan() else onRefresh() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isRefreshing) stringResource(R.string.connect_stop_scan)
                else stringResource(R.string.connect_rescan)
            )
        }

        Spacer(modifier = Modifier.height(ObdSpacing.CardGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ObdSpacing.CardGap)
        ) {
            ObdOutlinedButton(
                onClick = {
                    uiState.showWifiDialog.value = true
                    wifiDialogOpen = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.connect_wifi_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            ObdOutlinedButton(
                onClick = {
                    obdHelper.setupDemo()
                    DemoSession.remember(context)
                    navigateToHome()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.connect_demo_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(ObdSpacing.CardGap))
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
                TextButton(enabled = !isConnecting, onClick = {
                    val host = wifiHost.trim()
                    val port = wifiPort.toIntOrNull()
                    if (host.isEmpty() || port == null || port !in 1..65535) {
                        connectError = context.getString(R.string.connect_wifi_invalid)
                        return@TextButton
                    }
                    connectError = null
                    wifiDialogOpen = false
                    uiState.showWifiDialog.value = false
                    uiState.wifiHost.value = host
                    uiState.wifiPort.value = port.toString()
                    scope.launch {
                        connectingLabel = "$host:$port"
                        uiState.isConnecting.value = true
                        try {
                            obdHelper.setupWifi(host, port)
                            obdHelper.initializeObd()
                            TransportMemory.remember(context, ObdTransport.WIFI)
                            navigateToHome()
                        } catch (e: Exception) {
                            Log.e("ConnectFragment", "WiFi connection failed", e)
                            connectError = failureDetail(e, unknownDetail)
                        } finally {
                            uiState.isConnecting.value = false
                            connectingLabel = null
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

/**
 * Nothing to connect to yet. Says what "nothing" means and what to do next,
 * instead of one raw sentence with an error string pasted into it.
 */
@Composable
private fun EmptyAdaptersCard(scanError: String?) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                // Neutral guidance, not the brand red: nothing failed here.
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.connect_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.connect_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (scanError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        // The scan failure keeps its real reason, just demoted
                        // from "the whole message" to a detail line.
                        text = scanError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** Named progress: "Connecting to 192.168.0.10:35000…", not a generic spinner. */
@Composable
private fun ConnectingCard(target: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.connect_connecting_to, target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Persisted failure notice: a cause the app is sure about, plus the adapter's
 * own words underneath. A toast is gone before you can act on it, and
 * "Could not connect" without the reason is not a diagnosis.
 */
@Composable
private fun ConnectFailureCard(detail: String) {
    val error = MaterialTheme.colorScheme.error
    val kind = ConnectFailureKind.classify(detail)
    val title = when (kind) {
        ConnectFailureKind.UNREACHABLE -> stringResource(R.string.connect_failed_unreachable)
        ConnectFailureKind.NOT_PAIRED -> stringResource(R.string.connect_failed_not_paired)
        ConnectFailureKind.BLUETOOTH_UNAVAILABLE -> stringResource(R.string.connect_failed_bluetooth)
        ConnectFailureKind.GENERIC -> stringResource(R.string.connect_failed_generic)
    }
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = error,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 1.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = error
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // The technical detail stays available, demoted below the
                    // plain-language cause instead of being the whole message.
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The adapter's own message, or an honest "no details" instead of a blank. */
private fun failureDetail(e: Exception, fallback: String): String =
    e.message?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

/** Single selectable adapter row, styled as a radio card. */
@Composable
private fun DeviceRow(
    device: BluetoothDeviceDTO,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) {
        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(ObdRadius.Inner),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = border,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
