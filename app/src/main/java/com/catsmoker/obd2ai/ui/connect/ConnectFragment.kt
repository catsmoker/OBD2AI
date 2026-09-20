package com.catsmoker.obd2ai.ui.connect

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.BluetoothDeviceDTO
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class BluetoothRecyclerViewAdapter(
    private val onItemClick: (BluetoothDeviceDTO) -> Unit
) : RecyclerView.Adapter<BluetoothRecyclerViewAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDeviceDTO>()

    fun updateDevices(newDevices: List<BluetoothDeviceDTO>) {
        val diffCallback = BluetoothDeviceDiffCallback(devices, newDevices)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        devices.clear()
        devices.addAll(newDevices)
        diffResult.dispatchUpdatesTo(this)
    }

    fun addDevice(device: BluetoothDeviceDTO) {
        if (!devices.any { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val deviceNameTextView: TextView = view.findViewById(R.id.deviceNameTextView)
        fun bind(device: BluetoothDeviceDTO, onItemClick: (BluetoothDeviceDTO) -> Unit) {
            deviceNameTextView.text = device.name
            itemView.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onItemClick(device)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position], onItemClick)
    }

    override fun getItemCount(): Int = devices.size
}

class BluetoothDeviceDiffCallback(
    private val oldList: List<BluetoothDeviceDTO>,
    private val newList: List<BluetoothDeviceDTO>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].address == newList[newItemPosition].address
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}


class ConnectFragment : Fragment() {
    private var deviceAddress: String = ""
    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var obdHelper: ObdHelper
    private lateinit var bluetoothAdapter: BluetoothRecyclerViewAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshDeviceList()
        } else {
            Toast.makeText(context, R.string.permissions_bluetooth_required, Toast.LENGTH_LONG).show()
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (it.name != null) {
                            bluetoothAdapter.addDevice(bluetoothHelper.convertToDeviceDTO(it))
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_connect, container, false)
        bluetoothHelper = (activity as MainActivity).bluetoothHelper
        obdHelper = (activity as MainActivity).obdHelper

        val btnConnect = view.findViewById<Button>(R.id.button_connect)
        val textSelected = view.findViewById<TextView>(R.id.textViewSelectedDev)
        val recyclerView = view.findViewById<RecyclerView>(R.id.devicesView)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

        bluetoothAdapter = BluetoothRecyclerViewAdapter { device ->
            deviceAddress = device.address
            textSelected.text = getString(R.string.connected_to, device.name)
            btnConnect.visibility = View.VISIBLE
        }
        recyclerView.adapter = bluetoothAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        swipeRefreshLayout.setOnRefreshListener {
            refreshDeviceList()
        }

        btnConnect.setOnClickListener {
            if (deviceAddress.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        btnConnect.isEnabled = false
                        Toast.makeText(context, R.string.connect_scanning_for_devices, Toast.LENGTH_LONG).show()
                        obdHelper.setupObd(deviceAddress)
                        obdHelper.initializeObd()
                        findNavController().navigate(R.id.action_connectFragment_to_errorOverviewFragment)
                    } catch (e: Exception) {
                        Log.e("ConnectFragment", "Connection failed", e)
                        Toast.makeText(context, getString(R.string.could_not_connect_to_obd2_adapter, e.message), Toast.LENGTH_LONG).show()
                        btnConnect.isEnabled = true
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.button_demo).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            obdHelper.setupDemo()
            findNavController().navigate(R.id.action_connectFragment_to_errorOverviewFragment)
        }

        view.findViewById<Button>(R.id.button_wifi).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showWifiDialog()
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        ContextCompat.registerReceiver(
            requireActivity(),
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        return view
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothHelper.isBluetoothEnabled()) {
            promptToEnableBluetooth()
        } else {
            refreshDeviceList()
        }
    }

    private fun promptToEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)
    }

    private fun refreshDeviceList() {
        swipeRefreshLayout.isRefreshing = true
        try {
            val pairedDevices = bluetoothHelper.getPairedDevices()
            bluetoothAdapter.updateDevices(pairedDevices)
            bluetoothHelper.startDiscovery()
            Handler(Looper.getMainLooper()).postDelayed({
                bluetoothHelper.stopDiscovery()
            }, 12000)
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.connect_no_devices_found, e.message), Toast.LENGTH_SHORT).show()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun showWifiDialog() {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, (8 * density).toInt(), pad, 0)
        }
        val hostInput = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.connect_wifi_host_hint)
            setText(prefs.getString(PrefsKeys.WIFI_HOST, PrefsKeys.DEFAULT_WIFI_HOST))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val portInput = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.connect_wifi_port_hint)
            setText(prefs.getInt(PrefsKeys.WIFI_PORT, PrefsKeys.DEFAULT_WIFI_PORT).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        container.addView(hostInput)
        container.addView(portInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.connect_wifi_title)
            .setView(container)
            .setPositiveButton(R.string.connect_wifi_button) { dialog, _ ->
                dialog.dismiss()
                val host = hostInput.text.toString().trim()
                val port = portInput.text.toString().toIntOrNull()
                if (host.isEmpty() || port == null || port !in 1..65535) {
                    Toast.makeText(context, R.string.connect_wifi_invalid, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.edit {
                    putString(PrefsKeys.WIFI_HOST, host)
                    putInt(PrefsKeys.WIFI_PORT, port)
                }
                lifecycleScope.launch {
                    try {
                        Toast.makeText(context, R.string.connect_scanning_for_devices, Toast.LENGTH_LONG).show()
                        obdHelper.setupWifi(host, port)
                        obdHelper.initializeObd()
                        findNavController().navigate(R.id.action_connectFragment_to_errorOverviewFragment)
                    } catch (e: Exception) {
                        Log.e("ConnectFragment", "WiFi connection failed", e)
                        Toast.makeText(context, getString(R.string.could_not_connect_to_obd2_adapter, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothHelper.stopDiscovery()
        requireActivity().unregisterReceiver(discoveryReceiver)
    }
}
