package com.catsmoker.obd2ai.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.common.AppNav
import com.catsmoker.obd2ai.ui.common.ConnectionState
import com.catsmoker.obd2ai.ui.home.HomeScreen
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Welcome screen and state-aware main menu (start destination). Keeps the
 * original behavior — title, description, connect / demo actions, plus
 * connection-independent Settings/About cards — while the UI itself is
 * Compose. Demo sets up in place: the same menu re-renders with simulated
 * data, no parallel screens.
 */
class OnboardingFragment : Fragment() {

    /** Derived from [ObdHelper]'s existing flags, refreshed on resume. */
    private val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        refreshState()
        return ComposeView(requireContext()).apply {
            // Fragment views can be destroyed while the composition is still
            // alive; tie disposal to the view lifecycle.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by connectionState.collectAsStateWithLifecycle()
                Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
                    HomeScreen(
                        state = state,
                        onConnect = ::openConnect,
                        onTryDemo = ::startDemo,
                        onDisconnect = ::disconnect,
                        onOpenDashboard = {
                            openConnected(R.id.action_onboardingFragment_to_liveDataFragment)
                        },
                        onOpenDiagnostics = {
                            openConnected(R.id.action_onboardingFragment_to_errorOverviewFragment)
                        },
                        onOpenTrip = {
                            findNavController().navigate(R.id.action_onboardingFragment_to_tripFragment)
                        },
                        onOpenConsole = {
                            findNavController().navigate(R.id.action_onboardingFragment_to_consoleFragment)
                        },
                        onOpenSettings = {
                            findNavController().navigate(R.id.action_onboardingFragment_to_settingsFragment)
                        },
                        onOpenAbout = {
                            findNavController().navigate(R.id.action_onboardingFragment_to_aboutFragment)
                        },
                        // Bar mode: the dock owns top-level navigation, so the
                        // tile grid would duplicate it — Home is the connect
                        // screen only. Cards mode keeps the tiles.
                        showTiles = AppNav.isCardsMode(navStyleTag()) &&
                            state != ConnectionState.DISCONNECTED
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    /**
     * "Get Started" honours the speed source: GPS mode needs no adapter, so
     * it opens the dashboard directly. OBD mode asks for Bluetooth
     * permissions before the connect screen.
     */
    private fun openConnect() {
        val activity = activity as? MainActivity ?: return
        val prefs = activity.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val speedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)

        if (speedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
            findNavController().navigate(R.id.action_onboardingFragment_to_liveDataFragment)
        } else if (activity.bluetoothHelper.checkBluetoothPermissions()) {
            findNavController().navigate(R.id.action_onboardingFragment_to_connectFragment)
        } else {
            findNavController().navigate(R.id.action_onboardingFragment_to_permissionsFragment)
        }
    }

    /** Demo sets up in place; bar mode then lands on the dashboard. */
    private fun startDemo() {
        val activity = activity as? MainActivity ?: return
        activity.obdHelper.setupDemo()
        refreshState()
        if (!AppNav.isCardsMode(navStyleTag())) {
            // Bar mode: no Home menu — go straight into the app.
            findNavController().navigate(R.id.action_onboardingFragment_to_liveDataFragment)
        }
    }

    private fun navStyleTag(): String? =
        activity?.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(PrefsKeys.NAV_STYLE, null)

    private fun disconnect() {
        val activity = activity as? MainActivity ?: return
        activity.obdHelper.disconnectAll()
        refreshState()
    }

    /**
     * Renders the menu from the live connection state. Called on resume
     * (back from connect, dashboard teardown, ...) and after local changes.
     */
    private fun refreshState() {
        val activity = activity as? MainActivity ?: return
        connectionState.value = ConnectionState.resolve(
            activity.obdHelper.demoMode,
            activity.obdHelper.isConnected
        )
        // The dock/rail share this state: starting demo or connecting makes
        // the bar appear in place; disconnecting hides it again.
        activity.updateNavChrome()
    }

    /** Dashboard/diagnostics read from the adapter: detour via connect first. */
    private fun openConnected(actionId: Int) {
        val activity = activity as? MainActivity ?: return
        findNavController().navigate(
            if (activity.obdHelper.isConnected) actionId else R.id.action_onboardingFragment_to_connectFragment
        )
    }
}
