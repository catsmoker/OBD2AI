package com.catsmoker.obd2ai.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys

class OnboardingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        // Demo first: explore the full dashboard with simulated telemetry,
        // no Bluetooth pairing required.
        view.findViewById<Button>(R.id.button_try_demo).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            (activity as MainActivity).obdHelper.setupDemo()
            findNavController().navigate(R.id.action_onboardingFragment_to_liveDataFragment)
        }
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
