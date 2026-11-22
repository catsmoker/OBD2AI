package com.catsmoker.obd2ai.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.compose.runtime.livedata.observeAsState
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R

/**
 * Permissions destination: explains why Bluetooth + Location are needed,
 * then requests them. Once granted it pops itself and opens Connect — the
 * same flow as the old fragment, now a pure composable destination.
 */
@Composable
fun PermissionsDestination(navController: NavHostController) {
    val activity = LocalContext.current as MainActivity
    val bluetoothHelper = activity.bluetoothHelper
    val granted by bluetoothHelper.isBluetoothPermissionGranted.observeAsState()

    LaunchedEffect(granted) {
        if (granted == true) {
            navController.navigate("connect") {
                launchSingleTop = true
                popUpTo("home") { inclusive = false }
            }
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.permissions_title),
        onBack = null
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bluetooth),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.permissions_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            ObdButton(
                onClick = { bluetoothHelper.requestPermissions(activity) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.permissions_button_grant))
            }
        }
    }
}
