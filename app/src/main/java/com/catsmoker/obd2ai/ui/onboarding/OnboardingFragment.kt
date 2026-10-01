package com.catsmoker.obd2ai.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.common.ConnectionState

/**
 * Welcome screen and state-aware main menu (start destination). Keeps the
 * original hero — title, description, connect / demo actions — and shows
 * connection-independent sections always (settings, about) plus the data
 * screens only when [ConnectionState] allows. Demo sets up in place: the
 * same menu re-renders with simulated data, no parallel screens.
 */
class OnboardingFragment : Fragment() {
    private var statusValue: TextView? = null
    private var disconnectButton: Button? = null
    private var connectButton: Button? = null
    private var demoButton: Button? = null
    private var featureCards: List<View> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusValue = view.findViewById(R.id.homeStatusValue)
        disconnectButton = view.findViewById(R.id.buttonDisconnect)
        connectButton = view.findViewById(R.id.button_get_started)
        demoButton = view.findViewById(R.id.button_try_demo)
        // Data screens only: settings/about cards stay visible in every state.
        featureCards = listOf(
            view.findViewById(R.id.cardDashboard),
            view.findViewById(R.id.cardDiagnostics),
            view.findViewById(R.id.cardTrip),
            view.findViewById(R.id.cardConsole)
        )
        view.findViewById<Button>(R.id.button_get_started).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            val speedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)

            if (speedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
                findNavController().navigate(R.id.action_onboardingFragment_to_liveDataFragment)
            } else {
                val bluetoothHelper = (activity as MainActivity).bluetoothHelper
                if (bluetoothHelper.checkBluetoothPermissions()) {
                    findNavController().navigate(R.id.action_onboardingFragment_to_connectFragment)
                } else {
                    findNavController().navigate(R.id.action_onboardingFragment_to_permissionsFragment)
                }
            }
        }
        // Demo sets up in place: no navigation, the same menu re-renders
        // in demo state with simulated data behind the same screens.
        view.findViewById<Button>(R.id.button_try_demo).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            (activity as MainActivity).obdHelper.setupDemo()
            refreshState()
        }
        view.findViewById<Button>(R.id.buttonDisconnect).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            (activity as MainActivity).obdHelper.disconnectAll()
            refreshState()
        }
        view.findViewById<View>(R.id.cardDashboard).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openConnected(R.id.action_onboardingFragment_to_liveDataFragment)
        }
        view.findViewById<View>(R.id.cardDiagnostics).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openConnected(R.id.action_onboardingFragment_to_errorOverviewFragment)
        }
        view.findViewById<View>(R.id.cardTrip).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_onboardingFragment_to_tripFragment)
        }
        view.findViewById<View>(R.id.cardConsole).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_onboardingFragment_to_consoleFragment)
        }
        view.findViewById<View>(R.id.cardSettings).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_onboardingFragment_to_settingsFragment)
        }
        view.findViewById<View>(R.id.cardAbout).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_onboardingFragment_to_aboutFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statusValue = null
        disconnectButton = null
        connectButton = null
        demoButton = null
        featureCards = emptyList()
    }

    /**
     * Renders the menu from the live connection state. Disconnected: the two
     * entry actions (connect, demo) plus settings/about. Connected/demo: the
     * four data screens plus settings/about, with disconnect/exit-demo
     * instead of the entry actions — no action ever appears twice.
     * Called on resume (back from connect, dashboard teardown, ...) and
     * after local state changes.
     */
    private fun refreshState() {
        val status = statusValue ?: return
        val activity = activity as? MainActivity ?: return
        val state = ConnectionState.resolve(
            activity.obdHelper.demoMode,
            activity.obdHelper.isConnected
        )
        val linked = state != ConnectionState.DISCONNECTED
        status.text = when (state) {
            ConnectionState.DEMO -> getString(R.string.error_overview_demo_badge)
            ConnectionState.CONNECTED -> getString(R.string.home_status_connected)
            ConnectionState.DISCONNECTED -> getString(R.string.home_status_offline)
        }
        connectButton?.visibility = if (linked) View.GONE else View.VISIBLE
        demoButton?.visibility = if (linked) View.GONE else View.VISIBLE
        disconnectButton?.apply {
            visibility = if (linked) View.VISIBLE else View.GONE
            setText(
                if (state == ConnectionState.DEMO) R.string.menu_exit_demo
                else R.string.menu_disconnect
            )
        }
        featureCards.forEach { it.visibility = if (linked) View.VISIBLE else View.GONE }
    }

    /** Dashboard/diagnostics read from the adapter: detour via connect first. */
    private fun openConnected(actionId: Int) {
        val connected = (activity as MainActivity).obdHelper.isConnected
        findNavController().navigate(
            if (connected) actionId else R.id.action_onboardingFragment_to_connectFragment
        )
    }
}


class PermissionsFragment : Fragment() {
    private lateinit var bluetoothHelper: BluetoothHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_permissions, container, false)
        bluetoothHelper = (activity as MainActivity).bluetoothHelper

        bluetoothHelper.isBluetoothPermissionGranted.observe(viewLifecycleOwner, Observer { isGranted ->
            if (isGranted) {
                findNavController().navigate(R.id.action_permissions_to_connectFragment)
            }
        })

        view.findViewById<Button>(R.id.button_grant).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bluetoothHelper.requestPermissions(requireActivity())
        }
        return view
    }
}
