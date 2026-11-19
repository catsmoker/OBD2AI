package com.catsmoker.obd2ai.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme

/**
 * Permissions screen: explains why Bluetooth + Location are needed, then
 * requests them. Once granted it pops itself and opens Connect — the same
 * flow as the XML version, now in Compose.
 */
class PermissionsFragment : Fragment() {

    private lateinit var bluetoothHelper: BluetoothHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bluetoothHelper = (activity as MainActivity).bluetoothHelper

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
                    PermissionsScreen(
                        bluetoothHelper = bluetoothHelper,
                        onGrant = {
                            bluetoothHelper.requestPermissions(requireActivity())
                        },
                        navigateToConnect = {
                            findNavController().navigate(R.id.action_permissions_to_connectFragment)
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun PermissionsScreen(
        bluetoothHelper: BluetoothHelper,
        onGrant: () -> Unit,
        navigateToConnect: () -> Unit
    ) {
        LaunchedEffect(Unit) {
            bluetoothHelper.isBluetoothPermissionGranted.observe(viewLifecycleOwner) { isGranted ->
                if (isGranted) {
                    navigateToConnect()
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
                Text(
                    text = stringResource(R.string.permissions_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                ObdButton(
                    onClick = { onGrant() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.permissions_button_grant))
                }
            }
        }
    }
}
