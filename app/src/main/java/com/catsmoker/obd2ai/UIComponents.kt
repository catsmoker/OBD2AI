package com.catsmoker.obd2ai

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.BatteryManager
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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.anastr.speedviewlib.DeluxeSpeedView
import com.github.anastr.speedviewlib.SpeedView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.launch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.analytics.FirebaseAnalytics

// =================================================================================
// ADAPTERS
// =================================================================================

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


class ErrorOverviewRecyclerViewAdapter(
    private val onItemClick: (DtpCodeDTO) -> Unit
) : RecyclerView.Adapter<ErrorOverviewRecyclerViewAdapter.ErrorCodeViewHolder>() {

    private var errorCodes: List<DtpCodeDTO> = emptyList()

    fun updateErrorCodes(newErrorCodes: List<DtpCodeDTO>) {
        val diffCallback = DtpCodeDiffCallback(this.errorCodes, newErrorCodes)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.errorCodes = newErrorCodes
        diffResult.dispatchUpdatesTo(this)
    }

    class ErrorCodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.imageView_icon)
        private val title: TextView = view.findViewById(R.id.title)
        private val detail: TextView = view.findViewById(R.id.detail)

        fun bind(item: DtpCodeDTO, onItemClick: (DtpCodeDTO) -> Unit) {
            title.text = item.title
            detail.text = item.detail
            icon.setColorFilter(ErrorSeverity.getColor(item.severity))
            itemView.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ErrorCodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_error_overview_layout, parent, false)
        return ErrorCodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ErrorCodeViewHolder, position: Int) {
        holder.bind(errorCodes[position], onItemClick)
    }

    override fun getItemCount(): Int = errorCodes.size
}

class DtpCodeDiffCallback(
    private val oldList: List<DtpCodeDTO>,
    private val newList: List<DtpCodeDTO>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].errorCode == newList[newItemPosition].errorCode
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}


class SuggestionsRecycleViewAdapter(private val actions: List<String>) :
    RecyclerView.Adapter<SuggestionsRecycleViewAdapter.ActionViewHolder>() {

    class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = itemView.findViewById(R.id.textViewSuggestedAction)
        fun bind(action: String) {
            textView.text = action
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_error_detail_suggestions_layout, parent, false)
        return ActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(actions[position])
    }

    override fun getItemCount() = actions.size
}


// =================================================================================
// FRAGMENTS
// =================================================================================

class OnboardingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_get_started).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            val prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val speedSource = prefs.getString("speed_source", "obd2_device")

            if (speedSource == "this_device") {
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
            Toast.makeText(context, R.string.permissions_bluetooth_required, Toast.LENGTH_SHORT).show()
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

    inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? = when {
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

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        requireActivity().registerReceiver(discoveryReceiver, filter)

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

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothHelper.stopDiscovery()
        requireActivity().unregisterReceiver(discoveryReceiver)
    }
}

class ErrorOverviewFragment : Fragment() {
    private lateinit var errorViewAdapter: ErrorOverviewRecyclerViewAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var statsTextView: TextView
    private lateinit var obdHelper: ObdHelper
    private lateinit var openAIService: OpenAIService
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_error_overview, container, false)
        obdHelper = (activity as MainActivity).obdHelper
        openAIService = (activity as MainActivity).openAIService
        firebaseAnalytics = (activity as MainActivity).firebaseAnalytics

        statsTextView = view.findViewById(R.id.textView_stats)
        progressBar = view.findViewById(R.id.progressBar)

        setupRecyclerView(view)
        showLoading(false)

        view.findViewById<Button>(R.id.button_analyze).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            firebaseAnalytics.logEvent("analyze_clicked", null)
            loadAndAssessErrorCodes()
        }

        view.findViewById<Button>(R.id.button_live_data).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            firebaseAnalytics.logEvent("live_data_clicked", null)
            findNavController().navigate(R.id.action_errorOverviewFragment_to_liveDataFragment)
        }

        return view
    }

    private fun loadAndAssessErrorCodes() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val allCodes = (obdHelper.getDtpCodes() + obdHelper.getPendingDtpCodes() + obdHelper.getPermanentDtpCodes()).distinct()
                if (allCodes.isEmpty()) {
                    updateUI(emptyList())
                    return@launch
                }
                val results = allCodes.map { openAIService.getDtpCodeAssessment(it) }
                ObdDataHolder.dtpResults = results
                updateUI(results)
            } catch (e: Exception) {
                Log.e("ErrorOverviewFragment", "Error loading/assessing codes", e)
                showErrorDialog(getString(R.string.no_errors_found, e.message))
                updateUI(emptyList())
            }
        }
    }

    private fun updateUI(errorCodes: List<DtpCodeDTO>) {
        activity?.runOnUiThread {
            showLoading(false)
            if (errorCodes.isNotEmpty()) {
                val sortedCodes = errorCodes.sortedByDescending { it.severity.ordinal }
                val lowCount = sortedCodes.count { it.severity == ErrorSeverity.LOW }
                val medCount = sortedCodes.count { it.severity == ErrorSeverity.MEDIUM }
                val highCount = sortedCodes.count { it.severity == ErrorSeverity.HIGH }
                statsTextView.text = getString(R.string.error_overview_error_counts, lowCount, medCount, highCount)
                errorViewAdapter.updateErrorCodes(sortedCodes)
            } else {
                statsTextView.text = getString(R.string.error_overview_no_errors)
                errorViewAdapter.updateErrorCodes(emptyList())
            }
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.view_error_overview)
        errorViewAdapter = ErrorOverviewRecyclerViewAdapter { errorItem ->
            val action = ErrorOverviewFragmentDirections.actionErrorOverviewFragmentToErrorDetailFragment(errorItem.errorCode)
            findNavController().navigate(action)
        }
        recyclerView.adapter = errorViewAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showErrorDialog(text: String) {
        ErrorDialogFragment.newInstance(text).show(parentFragmentManager, "errorDialog")
    }
}


class ErrorDetailFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_error_detail, container, false)
        val errorCode = ErrorDetailFragmentArgs.fromBundle(requireArguments()).errorCode

        ObdDataHolder.dtpResults.find { it.errorCode == errorCode }?.let {
            renderErrorDetail(it, view)
        }
        return view
    }

    private fun renderErrorDetail(dto: DtpCodeDTO, view: View) {
        view.findViewById<TextView>(R.id.textViewErrorTitle).text = dto.title
        view.findViewById<TextView>(R.id.textViewErrorCode).text = dto.errorCode
        view.findViewById<TextView>(R.id.textViewErrorSeverity).text = getString(R.string.error_details_code_label, dto.severity)
        view.findViewById<TextView>(R.id.textViewErrorDetail).text = dto.detail
        view.findViewById<TextView>(R.id.textViewErrorImplications).text = dto.implications
        view.findViewById<ImageView>(R.id.imageView_icon).setColorFilter(ErrorSeverity.getColor(dto.severity))

        val recyclerView = view.findViewById<RecyclerView>(R.id.suggestedActionRecyclerView)
        recyclerView.adapter = SuggestionsRecycleViewAdapter(dto.suggestedActions)
        recyclerView.layoutManager = LinearLayoutManager(context)
    }
}


class LiveDataFragment : Fragment(), LocationListener {

    private lateinit var obdHelper: ObdHelper
    private var mediaPlayer: MediaPlayer? = null

    private var rpmTier = 0
    private var originalRpmColor = Color.BLACK

    private lateinit var locationManager: LocationManager
    private lateinit var speedView: DeluxeSpeedView
    private lateinit var batteryTempView: TextView
    private lateinit var coolantTempView: TextView

    private val batteryTempReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f)
            batteryTempView.text = "Device Temp: $temperature°C"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live_data, container, false)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        obdHelper = (activity as MainActivity).obdHelper

        speedView = view.findViewById<DeluxeSpeedView>(R.id.speedView2)
        val rpmView = view.findViewById<DeluxeSpeedView>(R.id.rpmView)
        batteryTempView = view.findViewById<TextView>(R.id.batteryTempView)
        coolantTempView = view.findViewById<TextView>(R.id.coolantTempView)

        originalRpmColor = rpmView.speedTextColor

        val prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val speedSource = prefs.getString("speed_source", "obd2_device")

        if (speedSource == "this_device") {
            rpmView.visibility = View.GONE
            batteryTempView.visibility = View.VISIBLE
            coolantTempView.visibility = View.GONE

            val constraintLayout = view as ConstraintLayout
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.connect(R.id.speedView2, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            constraintSet.connect(R.id.speedView2, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            constraintSet.applyTo(constraintLayout)

            locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1f, this)
            requireContext().registerReceiver(batteryTempReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } else {
            batteryTempView.visibility = View.GONE
            coolantTempView.visibility = View.VISIBLE
            lifecycleScope.launch {
                obdHelper.startLiveDataMonitoring()
            }

            lifecycleScope.launch {
                ObdDataHolder.speedFlow.collect { speedString ->
                    val speedValue = speedString.split(" ")[0].toFloatOrNull() ?: 0f
                    speedView.speedTo(speedValue, 400)
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.rpmFlow.collect { rpmString ->
                    val rpmValue = rpmString.split(" ")[0].toIntOrNull() ?: 0
                    rpmView.speedTo(rpmValue.toFloat() / 100, 400)

                    if (rpmValue > 4500 && rpmTier < 4) {
                        rpmTier = 4
                        playSound(R.raw.danger)
                        rpmView.speedTextColor = Color.RED
                        Handler(Looper.getMainLooper()).postDelayed({
                            rpmView.speedTextColor = originalRpmColor
                        }, 500)
                    }
                    else if (rpmValue > 3000 && rpmTier < 3) {
                        rpmTier = 3
                        playSound(R.raw.thirty)
                    }
                    else if (rpmValue > 2000 && rpmTier < 2) {
                        rpmTier = 2
                        playSound(R.raw.twenty)
                    }
                    else if (rpmValue > 1000 && rpmTier < 1) {
                        rpmTier = 1
                        playSound(R.raw.ten)
                    }

                    else if (rpmValue < 900 && rpmTier >= 1) {
                        rpmTier = 0
                    }
                    else if (rpmValue < 1900 && rpmTier >= 2) {
                        rpmTier = 1
                    }
                    else if (rpmValue < 2900 && rpmTier >= 3) {
                        rpmTier = 2
                    }
                    else if (rpmValue < 4400 && rpmTier >= 4) {
                        rpmTier = 3
                    }
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.coolantTempFlow.collect { coolantTempString ->
                    coolantTempView.text = "Coolant: $coolantTempString"
                }
            }
        }
    }

    override fun onLocationChanged(location: android.location.Location) {
        val speedKmh = location.speed * 3.6f
        speedView.speedTo(speedKmh)
    }

    override fun onProviderDisabled(provider: String) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun playSound(soundResId: Int) {
        val prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("mute_sound", false)) {
            return
        }

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, soundResId).apply {
            start()
            setOnCompletionListener { it.release() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(this)
        }
        val prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val speedSource = prefs.getString("speed_source", "obd2_device")
        if (speedSource == "this_device") {
            requireContext().unregisterReceiver(batteryTempReceiver)
        }
        obdHelper.disconnectFromObdDevice()
        obdHelper.stopLiveDataMonitoring()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchDarkMode = view.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        val prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("dark_mode", isChecked)
            }
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        val apiKeyEditText = view.findViewById<TextInputEditText>(R.id.apiKeyEditText)
        apiKeyEditText.setText(prefs.getString("openai_api_key", ""))

        val modelIdEditText = view.findViewById<TextInputEditText>(R.id.modelIdEditText)
        val saveButton = view.findViewById<Button>(R.id.saveButton)

        apiKeyEditText.setText(prefs.getString("openai_api_key", ""))
        modelIdEditText.setText(prefs.getString("openai_model_id", "gpt-5-mini"))

        val switchMuteSound = view.findViewById<SwitchMaterial>(R.id.switchMuteSound)

        switchMuteSound.isChecked = prefs.getBoolean("mute_sound", false)

        switchMuteSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("mute_sound", isChecked)
            }
        }

        val radioGroupSpeedSource = view.findViewById<RadioGroup>(R.id.radioGroupSpeedSource)
        val savedSpeedSource = prefs.getString("speed_source", "obd2_device")
        if (savedSpeedSource == "this_device") {
            radioGroupSpeedSource.check(R.id.radioButtonGps)
        } else {
            radioGroupSpeedSource.check(R.id.radioButtonObd2)
        }

        saveButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val selectedSpeedSource = if (radioGroupSpeedSource.checkedRadioButtonId == R.id.radioButtonGps) "this_device" else "obd2_device"
            prefs.edit {
                putString("openai_api_key", apiKeyEditText.text.toString())
                putString("openai_model_id", modelIdEditText.text.toString())
                putString("speed_source", selectedSpeedSource)
            }
            Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }
}


class ErrorDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_error_title)
            .setMessage(arguments?.getString(ARG_ERROR_MESSAGE))
            .setPositiveButton(R.string.onboarding_button_skip) { dialog, _ -> dialog.dismiss() }
            .create()
    }

    companion object {
        private const val ARG_ERROR_MESSAGE = "errorMessage"
        fun newInstance(errorMessage: String) = ErrorDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ERROR_MESSAGE, errorMessage)
            }
        }
    }
}