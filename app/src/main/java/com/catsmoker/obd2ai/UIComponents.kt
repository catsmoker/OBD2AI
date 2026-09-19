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
import android.graphics.Typeface
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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
import com.github.anastr.speedviewlib.TubeSpeedometer
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
        private val pillContainer: View = view.findViewById(R.id.pill_container)
        private val pillStored: TextView = view.findViewById(R.id.pill_stored)
        private val pillPending: TextView = view.findViewById(R.id.pill_pending)
        private val pillPermanent: TextView = view.findViewById(R.id.pill_permanent)

        fun bind(item: DtpCodeDTO, onItemClick: (DtpCodeDTO) -> Unit) {
            title.text = item.title
            detail.text = if (item.offline) {
                itemView.context.getString(R.string.error_offline_suffix, item.detail)
            } else {
                item.detail
            }
            icon.setColorFilter(ErrorSeverity.getColor(item.severity))
            bindPill(pillStored, item.sources.contains(DtcSource.STORED), R.string.dtc_source_stored)
            bindPill(pillPending, item.sources.contains(DtcSource.PENDING), R.string.dtc_source_pending)
            bindPill(pillPermanent, item.sources.contains(DtcSource.PERMANENT), R.string.dtc_source_permanent)
            // Older caches carry no sources: hide the row instead of guessing.
            pillContainer.visibility = if (item.sources.isEmpty()) View.GONE else View.VISIBLE
            itemView.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onItemClick(item)
            }
        }

        private fun bindPill(pill: TextView, shown: Boolean, labelRes: Int) {
            pill.visibility = if (shown) View.VISIBLE else View.GONE
            if (shown) pill.text = itemView.context.getString(labelRes)
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

/** One line in the Ask-AI follow-up chat: the driver's question or the AI answer. */
data class ChatMessage(val text: String, val fromUser: Boolean)

class ChatMessageAdapter :
    RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = itemView.findViewById(R.id.textViewChatMessage)

        fun bind(message: ChatMessage) {
            textView.text = message.text
            // Driver questions right-aligned + bold, AI answers left-aligned.
            textView.gravity = if (message.fromUser) Gravity.END else Gravity.START
            textView.setTypeface(
                textView.typeface,
                if (message.fromUser) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size
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
        androidx.core.content.ContextCompat.registerReceiver(
            requireActivity(),
            discoveryReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
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

        AlertDialog.Builder(requireContext())
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

class ErrorOverviewFragment : Fragment() {
    private lateinit var errorViewAdapter: ErrorOverviewRecyclerViewAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var statsTextView: TextView
    private lateinit var milTextView: TextView
    private lateinit var demoBadge: TextView
    private lateinit var adapterTextView: TextView
    private lateinit var obdHelper: ObdHelper
    private lateinit var aiService: AiService
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    companion object {
        private var cachedDictionary: Map<String, DtcInfo>? = null

        /** Bundled generic code knowledge, loaded once per process. */
        fun dictionaryFor(context: Context): Map<String, DtcInfo> {
            return cachedDictionary ?: runCatching {
                context.resources.openRawResource(R.raw.dtc_generic).bufferedReader().use { it.readText() }
            }.mapCatching { DtcDictionary.fromJson(it) }.getOrDefault(emptyMap()).also {
                cachedDictionary = it
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_error_overview, container, false)
        obdHelper = (activity as MainActivity).obdHelper
        aiService = (activity as MainActivity).aiService
        firebaseAnalytics = (activity as MainActivity).firebaseAnalytics

        statsTextView = view.findViewById(R.id.textView_stats)
        milTextView = view.findViewById(R.id.textView_mil)
        demoBadge = view.findViewById(R.id.textView_demo)
        adapterTextView = view.findViewById(R.id.textView_adapter)
        progressBar = view.findViewById(R.id.progressBar)

        demoBadge.visibility = if (obdHelper.demoMode) View.VISIBLE else View.GONE
        if (obdHelper.isConnected) {
            lifecycleScope.launch {
                val health = async { runCatching { obdHelper.getAdapterHealth() } }
                val vehicle = async { runCatching { obdHelper.getVehicleInfo() } }
                val healthResult = health.await().getOrNull()
                val vehicleResult = vehicle.await().getOrNull()
                activity?.runOnUiThread {
                    if (healthResult != null || vehicleResult != null) {
                        val lines = listOfNotNull(
                            healthResult?.let {
                                getString(R.string.error_overview_adapter_health, it.first, it.second, it.third)
                            },
                            vehicleResult?.let {
                                getString(R.string.error_overview_vehicle_info, it.first, it.second, it.third)
                            }
                        )
                        adapterTextView.text = lines.joinToString("\n")
                    } else {
                        adapterTextView.visibility = View.GONE
                    }
                }
            }
        } else {
            adapterTextView.visibility = View.GONE
        }

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

        view.findViewById<Button>(R.id.button_clear_codes).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            firebaseAnalytics.logEvent("clear_codes_clicked", null)
            confirmAndClearCodes()
        }

        view.findViewById<Button>(R.id.button_console).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            firebaseAnalytics.logEvent("console_clicked", null)
            findNavController().navigate(R.id.action_errorOverviewFragment_to_consoleFragment)
        }

        view.findViewById<Button>(R.id.button_trip).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            firebaseAnalytics.logEvent("trip_clicked", null)
            findNavController().navigate(R.id.action_errorOverviewFragment_to_tripFragment)
        }

        return view
    }

    private fun loadAndAssessErrorCodes() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                // Each mode is read independently: one failing mode (e.g. permanent
                // codes unsupported) no longer discards the other two. Only when all
                // three fail do we treat it as a connection error.
                val stored = async { runCatching { obdHelper.getDtpCodes() } }
                val pending = async { runCatching { obdHelper.getPendingDtpCodes() } }
                val permanent = async { runCatching { obdHelper.getPermanentDtpCodes() } }
                val mil = async { runCatching { obdHelper.getMilStatus() } }
                val reads = listOf(stored.await(), pending.await(), permanent.await())
                if (reads.all { it.isFailure }) {
                    throw reads.firstNotNullOf { it.exceptionOrNull() }
                }
                val allCodes = reads.mapNotNull { it.getOrNull() }.flatten().distinct()
                // Remember which mode reported each code so the list can show
                // Stored/Pending/Permanent pills. Lookups normalize case.
                val sourceMap = ObdHelper.mergeCodeSources(
                    stored.await().getOrDefault(emptyList()),
                    pending.await().getOrDefault(emptyList()),
                    permanent.await().getOrDefault(emptyList())
                )
                updateMilStatus(mil.await().getOrNull())
                if (allCodes.isEmpty()) {
                    ObdDataHolder.dtpResults = emptyList()
                    DtcStore.save(requireContext(), emptyList())
                    updateUI(emptyList(), allOffline = false)
                    return@launch
                }
                // Without an API key there is no point firing N doomed network
                // calls; assess offline straight away. With a key, one failed
                // request degrades to offline info instead of failing the batch.
                val useOffline = !aiService.hasApiKey()
                val dict = dictionaryFor(requireContext())
                val results = allCodes.map { code ->
                    async {
                        val entry = DtcDictionary.lookup(code, dict)
                        val assessed = if (useOffline) {
                            AiService.buildOfflineAssessment(code, entry)
                        } else {
                            runCatching { aiService.getDtpCodeAssessment(code) }
                                .getOrElse { AiService.buildOfflineAssessment(code, entry) }
                        }
                        assessed.copy(sources = sourceMap[code.trim().uppercase()] ?: emptySet())
                    }
                }.awaitAll()
                ObdDataHolder.dtpResults = results
                DtcStore.save(requireContext(), results)
                updateUI(results, allOffline = results.all { it.offline })
            } catch (e: Exception) {
                Log.e("ErrorOverviewFragment", "Error loading/assessing codes", e)
                showErrorDialog(getString(R.string.no_errors_found, e.message))
                updateUI(emptyList(), allOffline = false)
            }
        }
    }

    private fun confirmAndClearCodes() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.error_overview_clear_confirm_title)
            .setMessage(R.string.error_overview_clear_confirm_message)
            .setPositiveButton(R.string.dialog_clear) { dialog, _ ->
                dialog.dismiss()
                lifecycleScope.launch {
                    try {
                        obdHelper.clearTroubleCodes()
                        ObdDataHolder.dtpResults = emptyList()
                        DtcStore.save(requireContext(), emptyList())
                        updateMilStatus(null)
                        updateUI(emptyList(), allOffline = false)
                        Toast.makeText(context, R.string.error_overview_clear_done, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("ErrorOverviewFragment", "Error clearing codes", e)
                        showErrorDialog(getString(R.string.no_errors_found, e.message))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateMilStatus(status: MilStatus?) {
        ObdDataHolder.lastMil = status
        activity?.runOnUiThread {
            milTextView.text = when {
                status == null -> getString(R.string.error_overview_mil_unknown)
                status.milOn -> getString(R.string.error_overview_mil_on, status.storedCount)
                else -> getString(R.string.error_overview_mil_off)
            }
        }
    }

    private fun updateUI(errorCodes: List<DtpCodeDTO>, allOffline: Boolean) {
        activity?.runOnUiThread {
            showLoading(false)
            if (errorCodes.isNotEmpty()) {
                val sortedCodes = errorCodes.sortedByDescending { it.severity.ordinal }
                val lowCount = sortedCodes.count { it.severity == ErrorSeverity.LOW }
                val medCount = sortedCodes.count { it.severity == ErrorSeverity.MEDIUM }
                val highCount = sortedCodes.count { it.severity == ErrorSeverity.HIGH }
                var stats = getString(R.string.error_overview_error_counts, lowCount, medCount, highCount)
                if (allOffline) {
                    stats += " · " + getString(R.string.error_overview_offline_note)
                }
                statsTextView.text = stats
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


/** Localized pill/source name for one read mode. */
private fun sourceLabel(context: Context, source: DtcSource): String = context.getString(
    when (source) {
        DtcSource.STORED -> R.string.dtc_source_stored
        DtcSource.PENDING -> R.string.dtc_source_pending
        DtcSource.PERMANENT -> R.string.dtc_source_permanent
    }
)

class ErrorDetailFragment : Fragment() {
    private lateinit var obdHelper: ObdHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_error_detail, container, false)
        val errorCode = ErrorDetailFragmentArgs.fromBundle(requireArguments()).errorCode
        obdHelper = (activity as MainActivity).obdHelper

        // Results live in memory, but fall back to the last cached scan so the
        // detail screen still works after process death.
        val dto = ObdDataHolder.dtpResults.find { it.errorCode == errorCode }
            ?: DtcStore.load(requireContext()).find { it.errorCode == errorCode }
        if (dto != null) {
            renderErrorDetail(dto, view)
            loadFreezeFrame(view)
            view.findViewById<Button>(R.id.buttonShare).setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                shareReport(dto)
            }
        } else {
            ErrorDialogFragment.newInstance(getString(R.string.error_detail_gone, errorCode))
                .show(parentFragmentManager, "errorDialog")
        }
        return view
    }

    /** Mode-02 snapshot: sensor values the ECU stored when the fault was set. */
    private fun loadFreezeFrame(view: View) {
        val freezeView = view.findViewById<TextView>(R.id.textViewFreezeData)
        lifecycleScope.launch {
            val freeze = runCatching { obdHelper.getFreezeFrame() }.getOrNull()
            activity?.runOnUiThread {
                val lines = listOfNotNull(
                    freeze?.dtc?.let { getString(R.string.freeze_dtc, it) },
                    freeze?.rpm?.let { getString(R.string.freeze_rpm, it) },
                    freeze?.speedKmh?.let { getString(R.string.freeze_speed, it) },
                    freeze?.coolantC?.let { getString(R.string.freeze_coolant, it) },
                    freeze?.loadPct?.let { getString(R.string.freeze_load, it) }
                )
                freezeView.text = lines.ifEmpty {
                    listOf(getString(R.string.freeze_unavailable))
                }.joinToString("\n")
                freezeView.visibility = View.VISIBLE
            }
        }
    }

    /** Shares this fault's assessment as plain text (mechanic, notes app…). */
    private fun shareReport(dto: DtpCodeDTO) {
        val body = buildString {
            appendLine("${dto.errorCode} — ${dto.title}")
            appendLine(getString(R.string.error_details_code_label, dto.severity))
            if (dto.sources.isNotEmpty()) {
                val names = dto.sources.sortedBy { it.ordinal }
                    .joinToString(", ") { sourceLabel(requireContext(), it) }
                appendLine(getString(R.string.error_detail_sources, names))
            }
            appendLine()
            appendLine(dto.detail)
            appendLine()
            appendLine(dto.implications)
            if (dto.suggestedActions.isNotEmpty()) {
                appendLine()
                dto.suggestedActions.forEach { appendLine("- $it") }
            }
            appendLine()
            append(getString(R.string.app_name))
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${dto.errorCode} ${dto.title}")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(share, getString(R.string.error_detail_share)))
    }

    private fun renderErrorDetail(dto: DtpCodeDTO, view: View) {
        view.findViewById<TextView>(R.id.textViewErrorTitle).text = dto.title
        view.findViewById<TextView>(R.id.textViewErrorCode).text = dto.errorCode
        view.findViewById<TextView>(R.id.textViewErrorSeverity).text = getString(R.string.error_details_code_label, dto.severity)
        view.findViewById<TextView>(R.id.textViewErrorDetail).text = dto.detail
        view.findViewById<TextView>(R.id.textViewErrorImplications).text = dto.implications
        view.findViewById<ImageView>(R.id.imageView_icon).setColorFilter(ErrorSeverity.getColor(dto.severity))

        val sourcesView = view.findViewById<TextView>(R.id.textViewErrorSources)
        if (dto.sources.isEmpty()) {
            sourcesView.visibility = View.GONE
        } else {
            val names = dto.sources.sortedBy { it.ordinal }
                .joinToString(", ") { sourceLabel(view.context, it) }
            sourcesView.text = getString(R.string.error_detail_sources, names)
            sourcesView.visibility = View.VISIBLE
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.suggestedActionRecyclerView)
        recyclerView.adapter = SuggestionsRecycleViewAdapter(dto.suggestedActions)
        recyclerView.layoutManager = LinearLayoutManager(context)

        view.findViewById<Button>(R.id.buttonAskAi).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val action = ErrorDetailFragmentDirections.actionErrorDetailFragmentToAskAiFragment(dto.errorCode)
            findNavController().navigate(action)
        }
    }
}


class AskAiFragment : Fragment() {
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var messagesView: RecyclerView
    private lateinit var questionEdit: EditText
    private lateinit var sendButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var noKeyHint: TextView
    private lateinit var obdHelper: ObdHelper
    private lateinit var aiService: AiService
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var dto: DtpCodeDTO? = null
    private var vin: String? = null
    private val history = mutableListOf<Pair<String, String>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_ask_ai, container, false)
        val errorCode = AskAiFragmentArgs.fromBundle(requireArguments()).errorCode
        obdHelper = (activity as MainActivity).obdHelper
        aiService = (activity as MainActivity).aiService
        firebaseAnalytics = (activity as MainActivity).firebaseAnalytics

        // Same lookup as the detail screen: memory first, cached scan second.
        dto = ObdDataHolder.dtpResults.find { it.errorCode == errorCode }
            ?: DtcStore.load(requireContext()).find { it.errorCode == errorCode }
        val current = dto
        if (current == null) {
            ErrorDialogFragment.newInstance(getString(R.string.error_detail_gone, errorCode))
                .show(parentFragmentManager, "errorDialog")
            return view
        }

        view.findViewById<TextView>(R.id.textViewAskWelcome).text =
            getString(R.string.ask_ai_welcome, current.errorCode)
        noKeyHint = view.findViewById(R.id.textViewAskNoKey)
        refreshNoKeyHint()

        chatAdapter = ChatMessageAdapter()
        messagesView = view.findViewById(R.id.viewAskMessages)
        messagesView.adapter = chatAdapter
        messagesView.layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }

        progressBar = view.findViewById(R.id.progressAsk)
        questionEdit = view.findViewById(R.id.editAskQuestion)
        questionEdit.hint = getString(R.string.ask_ai_hint, current.errorCode)
        questionEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick()
                true
            } else {
                false
            }
        }
        sendButton = view.findViewById(R.id.buttonAskSend)
        sendButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            sendQuestion()
        }

        // Best effort: VIN enriches the AI context. Demo returns its fake VIN
        // too; "?" or blank means unknown and is left out of the prompt.
        lifecycleScope.launch {
            vin = runCatching { obdHelper.getVehicleInfo().first }.getOrNull()
                ?.takeIf { it.isNotBlank() && it != "?" }
        }
        return view
    }

    private fun refreshNoKeyHint() {
        noKeyHint.visibility = if (aiService.hasApiKey()) View.GONE else View.VISIBLE
    }

    private fun sendQuestion() {
        val current = dto ?: return
        val question = questionEdit.text.toString().trim()
        if (question.isEmpty()) return
        questionEdit.text.clear()
        chatAdapter.addMessage(ChatMessage(question, fromUser = true))
        messagesView.scrollToPosition(chatAdapter.itemCount - 1)
        setThinking(true)
        lifecycleScope.launch {
            try {
                val answer = aiService.askFollowUp(
                    current, ObdDataHolder.lastMil, vin, history.toList(), question
                )
                history.add(question to answer)
                if (history.size > 10) history.removeAt(0)
                activity?.runOnUiThread {
                    setThinking(false)
                    refreshNoKeyHint()
                    chatAdapter.addMessage(ChatMessage(answer, fromUser = false))
                    messagesView.scrollToPosition(chatAdapter.itemCount - 1)
                    firebaseAnalytics.logEvent("ask_ai_sent", null)
                }
            } catch (e: Exception) {
                Log.e("AskAiFragment", "Follow-up answer failed", e)
                activity?.runOnUiThread {
                    setThinking(false)
                    refreshNoKeyHint()
                    ErrorDialogFragment.newInstance(getString(R.string.ask_ai_failed, e.message))
                        .show(parentFragmentManager, "errorDialog")
                }
            }
        }
    }

    private fun setThinking(thinking: Boolean) {
        progressBar.visibility = if (thinking) View.VISIBLE else View.GONE
        sendButton.isEnabled = !thinking
    }
}


/**
 * Raw OBD console for power users: type any AT command or PID ("010C"),
 * see the adapter's raw reply. Shares the connection, so avoid it while
 * Live Data is polling. Transcript is capped by [ConsoleLog].
 */
class ConsoleFragment : Fragment() {
    private lateinit var scrollView: View
    private lateinit var logView: TextView
    private lateinit var input: EditText
    private lateinit var obdHelper: ObdHelper
    private val transcript = ConsoleLog()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_console, container, false)
        obdHelper = (activity as MainActivity).obdHelper

        scrollView = view.findViewById(R.id.scrollConsole)
        logView = view.findViewById(R.id.textViewConsoleLog)
        input = view.findViewById(R.id.editConsoleInput)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                view.findViewById<Button>(R.id.buttonConsoleSend).performClick()
                true
            } else {
                false
            }
        }
        view.findViewById<Button>(R.id.buttonConsoleSend).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            sendCommand()
        }
        view.findViewById<Button>(R.id.buttonConsoleClear).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            transcript.clear()
            render()
        }
        if (!obdHelper.isConnected) {
            transcript.append(getString(R.string.obd_console_not_connected))
            render()
        }
        return view
    }

    private fun sendCommand() {
        val cmd = input.text.toString().trim()
        if (cmd.isEmpty()) return
        input.text.clear()
        transcript.append("> $cmd")
        render()
        lifecycleScope.launch {
            try {
                val reply = obdHelper.sendRaw(cmd)
                activity?.runOnUiThread {
                    transcript.append(reply.trimEnd())
                    render()
                }
            } catch (e: Exception) {
                Log.e("ConsoleFragment", "Raw command failed", e)
                activity?.runOnUiThread {
                    transcript.append("Error: ${e.message}")
                    render()
                }
            }
        }
    }

    private fun render() {
        logView.text = transcript.snapshot().joinToString("\n")
        scrollView.post { scrollView.scrollTo(0, logView.bottom) }
    }
}

/**
 * Trip computer screen: start/stop/reset plus time, distance, average and
 * top speed, top RPM and idle time. Samples the shared SI flows once a
 * second; display converts to the user's units. Nothing is persisted.
 */
class TripFragment : Fragment() {
    private val trip = TripComputer()
    private var imperial = false
    private lateinit var durationView: TextView
    private lateinit var distanceView: TextView
    private lateinit var avgView: TextView
    private lateinit var maxSpeedView: TextView
    private lateinit var maxRpmView: TextView
    private lateinit var idleView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_trip, container, false)
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        imperial = Units.isImperial(prefs)

        durationView = view.findViewById(R.id.tripDuration)
        distanceView = view.findViewById(R.id.tripDistance)
        avgView = view.findViewById(R.id.tripAvg)
        maxSpeedView = view.findViewById(R.id.tripMaxSpeed)
        maxRpmView = view.findViewById(R.id.tripMaxRpm)
        idleView = view.findViewById(R.id.tripIdle)
        startButton = view.findViewById(R.id.tripStart)
        stopButton = view.findViewById(R.id.tripStop)

        startButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            trip.start(System.currentTimeMillis())
            refreshButtons()
        }
        stopButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            trip.stop(System.currentTimeMillis())
            refreshButtons()
            render(System.currentTimeMillis())
        }
        view.findViewById<Button>(R.id.tripReset).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            trip.reset()
            refreshButtons()
            render(System.currentTimeMillis())
        }
        refreshButtons()
        render(System.currentTimeMillis())

        lifecycleScope.launch {
            while (isAdded) {
                val speed = ObdDataHolder.speedFlow.value.split(" ")[0].toFloatOrNull()?.toDouble()
                val rpm = ObdDataHolder.rpmFlow.value.split(" ")[0].toIntOrNull()
                trip.sample(speed, rpm, System.currentTimeMillis())
                activity?.runOnUiThread { render(System.currentTimeMillis()) }
                delay(1000)
            }
        }
        return view
    }

    private fun refreshButtons() {
        startButton.isEnabled = !trip.isRunning
        stopButton.isEnabled = trip.isRunning
    }

    private fun speedText(kmh: Double): String {
        return if (imperial) {
            getString(R.string.trip_speed_mph, Units.displaySpeed(kmh, true).toInt())
        } else {
            getString(R.string.trip_speed_kmh, kmh.toInt())
        }
    }

    private fun render(nowMs: Long) {
        val snap = trip.snapshot(nowMs)
        durationView.text = getString(R.string.trip_duration, TripComputer.formatDuration(snap.durationMs))
        distanceView.text = getString(
            R.string.trip_distance,
            if (imperial) getString(R.string.trip_dist_mi, Units.kmhToMph(snap.distanceKm))
            else getString(R.string.trip_dist_km, snap.distanceKm)
        )
        avgView.text = getString(R.string.trip_avg_speed, speedText(snap.avgKmh))
        maxSpeedView.text = getString(R.string.trip_max_speed, speedText(snap.maxKmh))
        maxRpmView.text = getString(R.string.trip_max_rpm, snap.maxRpm)
        idleView.text = getString(R.string.trip_idle, TripComputer.formatDuration(snap.idleMs))
    }
}

/**
 * Optional entertainment: an RPM-driven engine hum synthesized on-device
 * (sine + harmonics through AudioTrack, no samples, no network). Frequency
 * follows RPM, gain fades with silence at idle. Fully isolated: it only
 * reads [rpm], never touches diagnostics, and dies with mute or the screen.
 */
class EngineSound {
    @Volatile var rpm: Int = 0
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        if (running.getAndSet(true)) return
        thread = Thread({ render() }, "EngineSound").apply { isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        runCatching { thread?.join(600) }
        thread = null
    }

    private fun render() {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(4096))
            .build()
        try {
            track.play()
            var phase = 0.0
            var freq = 70.0
            var gain = 0.0
            val chunk = ShortArray(1024)
            val tau = 2.0 * Math.PI
            while (running.get()) {
                val targetFreq = 55.0 + rpm.coerceIn(0, 8000) * 0.028
                freq += (targetFreq - freq) * 0.08
                val targetGain = if (rpm > 400) 0.5 else 0.0
                gain += (targetGain - gain) * 0.05
                for (i in chunk.indices) {
                    phase = (phase + tau * freq / sampleRate) % tau
                    val s = (Math.sin(phase) * 0.55 +
                        Math.sin(phase * 2.0) * 0.28 +
                        Math.sin(phase * 3.0) * 0.17) * gain
                    chunk[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(chunk, 0, chunk.size)
            }
            // Fade out so stopping does not click.
            repeat(5) {
                gain *= 0.6f
                for (i in chunk.indices) {
                    phase = (phase + tau * freq / sampleRate) % tau
                    val s = (Math.sin(phase) * 0.55 +
                        Math.sin(phase * 2.0) * 0.28 +
                        Math.sin(phase * 3.0) * 0.17) * gain
                    chunk[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(chunk, 0, chunk.size)
            }
        } catch (e: Exception) {
            Log.e("EngineSound", "Playback failed", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}


class LiveDataFragment : Fragment(), LocationListener {

    /** Logical Offline AI events — detectors and UI use these, never filenames. */
    enum class OfflineAiEvent {
        WELCOME,
        SHIFT_POINT,
        HIGH_RPM,
        HIGH_SPEED,
        COOLANT_HIGH,
        COOLANT_OK,
        NEW_FAULT_CODE,
        CONNECTION_LOST,
        CONNECTION_RESTORED,
        ENGINE_STARTED,
        ENGINE_STOPPED
    }

    /** Which assistant a master switch belongs to (mutual-exclusion helper). */
    enum class AiSystem { OFFLINE, ONLINE }

    private lateinit var obdHelper: ObdHelper
    private var mediaPlayer: MediaPlayer? = null
    private var tts: android.speech.tts.TextToSpeech? = null

    companion object {
        private const val RPM_SLIDER_OFFSET = 800
        private const val COOLANT_SLIDER_OFFSET = 50

        private const val RPM_GREEN = 0xFF43A047.toInt()
        private const val RPM_AMBER = 0xFFF9A825.toInt()
        private const val RPM_RED = 0xFFE53935.toInt()

        /** Green/amber boundary derived from the shift point (idle stays green). */
        fun greenThreshold(shiftRpm: Int): Int =
            maxOf(shiftRpm - 1000, 1000)

        /** Amber/red boundary: the shift point itself. */
        fun amberThreshold(shiftRpm: Int): Int =
            maxOf(shiftRpm, greenThreshold(shiftRpm) + 500)

        /** Gauge zones on the x100-RPM dial, arranged around the shift point. */
        fun rpmZoneColor(rpmValue: Int, shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM): Int = when {
            rpmValue < greenThreshold(shiftRpm) -> RPM_GREEN
            rpmValue < amberThreshold(shiftRpm) -> RPM_AMBER
            else -> RPM_RED
        }

        /**
         * RPM dial zones as (startOffset, endOffset, color) fractions of [rpmMax].
         * Kept in one pure function so unit tests can pin the tiling: the gauge
         * library throws if a section conflicts with its neighbour.
         */
        fun rpmSections(
            rpmMax: Float,
            shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM
        ): List<Triple<Float, Float, Int>> {
            val greenEnd = (greenThreshold(shiftRpm) / 100f / rpmMax).coerceIn(0.05f, 0.9f)
            val amberEnd = (amberThreshold(shiftRpm) / 100f / rpmMax).coerceIn(greenEnd + 0.05f, 0.95f)
            return listOf(
                Triple(0f, greenEnd, RPM_GREEN),
                Triple(greenEnd, amberEnd, RPM_AMBER),
                Triple(amberEnd, 1f, RPM_RED)
            )
        }

        /** RPM warning triggers derived from the shift point (tier 4 = over-rev). */
        fun rpmTriggers(shiftRpm: Int): IntArray {
            val t1 = maxOf(shiftRpm - 2500, 800)
            val t2 = maxOf(shiftRpm - 1500, t1 + 500)
            val t3 = maxOf(shiftRpm - 500, t2 + 500)
            val t4 = maxOf(shiftRpm + 1000, t3 + 500)
            return intArrayOf(t1, t2, t3, t4)
        }

        /**
         * Pure RPM warning-tier state machine (hysteresis bands: trigger above,
         * release 100 RPM below). Extracted so the logic is unit-testable.
         */
        fun nextRpmTier(
            rpmValue: Int,
            currentTier: Int,
            shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM
        ): Int {
            val (t1, t2, t3, t4) = rpmTriggers(shiftRpm).let {
                listOf(it[0], it[1], it[2], it[3])
            }
            return when {
                rpmValue > t4 && currentTier < 4 -> 4
                rpmValue > t3 && currentTier < 3 -> 3
                rpmValue > t2 && currentTier < 2 -> 2
                rpmValue > t1 && currentTier < 1 -> 1
                rpmValue < t1 - 100 && currentTier >= 1 -> 0
                rpmValue < t2 - 100 && currentTier >= 2 -> 1
                rpmValue < t3 - 100 && currentTier >= 3 -> 2
                rpmValue < t4 - 100 && currentTier >= 4 -> 3
                else -> currentTier
            }
        }

        /**
         * Pure coolant-alarm latch with hysteresis: trips at
         * [PrefsKeys.COOLANT_ALARM_C], releases below
         * [PrefsKeys.COOLANT_ALARM_RELEASE_C] so the siren does not flap.
         */
        fun nextCoolantAlarmed(coolantC: Int?, currentlyAlarmed: Boolean): Boolean = when {
            currentlyAlarmed -> (coolantC ?: 0) >= PrefsKeys.COOLANT_ALARM_RELEASE_C
            else -> (coolantC ?: 0) >= PrefsKeys.COOLANT_ALARM_C
        }

        // -- Offline AI proactive cues ------------------------------------------------
        const val OFFLINE_AI_SHIFT_COOLDOWN_MS = 15_000L
        const val OFFLINE_AI_EVENT_COOLDOWN_MS = 30_000L

        /** True only on the rising edge across [threshold] (no repeat while held). */
        fun shiftCrossed(prevRpm: Int, newRpm: Int, threshold: Int): Boolean =
            prevRpm < threshold && newRpm >= threshold

        /** True only on the falling edge across [threshold] (no repeat while held). */
        fun crossedDown(prevValue: Int, newValue: Int, threshold: Int): Boolean =
            prevValue >= threshold && newValue < threshold

        /** Cooldown gate so Offline AI nags at most once per [cooldownMs]. */
        fun offlineAiReady(lastFiredMs: Long, nowMs: Long, cooldownMs: Long): Boolean =
            nowMs - lastFiredMs >= cooldownMs

        /**
         * Popup linger: one second per word so the line stays readable while
         * driving (2 s floor so tiny cues don't just flash).
         */
        fun bannerDurationMs(text: String): Long {
            val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            return maxOf(2, words) * 1000L
        }

        // -- Easter eggs (Offline AI only; never Online AI) --------------------
        const val POOL_SPEED_130 = "speed_130"
        const val POOL_SPEED_140 = "speed_140"
        const val POOL_SPEED_150 = "speed_150"
        const val POOL_RPM = "rpm"
        const val POOL_COMBO = "combo"
        const val POOL_COOLANT_AFTER = "coolant_after"
        const val POOL_FAULT_NEW = "fault_new"
        const val POOL_FAULT_RETURN = "fault_return"
        const val POOL_FAULT_SPEEDING = "fault_speeding"
        const val POOL_ULTRA = "ultra"
        const val EASTER_RECENT_MAX = 8

        /** Speed Easter-egg tier: 0 = none, 1 = 130+, 2 = 140+, 3 = 150+. */
        fun speedEasterTier(speedKmh: Float): Int = when {
            speedKmh >= 150 -> 3
            speedKmh >= 140 -> 2
            speedKmh >= 130 -> 1
            else -> 0
        }

        fun poolForSpeedTier(tier: Int): String = when (tier) {
            3 -> POOL_SPEED_150
            2 -> POOL_SPEED_140
            else -> POOL_SPEED_130
        }

        /** True when RPM and speed are both unusually high at once. */
        fun comboActive(rpm: Int, rpmThreshold: Int, speedKmh: Float, speedThreshold: Int): Boolean =
            rpm >= rpmThreshold && speedKmh >= speedThreshold.toFloat()

        /** Probability gate: [random01] must fall below [probability]. */
        fun rollEasterEgg(random01: Float, probability: Float): Boolean =
            random01 in 0f..<probability

        /**
         * Picks the next joke, skipping recently played lines (rotating from
         * [startIndex] so repeats spread out). Null when the pool is empty
         * or every line played recently — then stay silent, don't force one.
         */
        fun pickFreshLine(
            pool: List<String>,
            recent: ArrayDeque<String>,
            startIndex: Int
        ): String? {
            if (pool.isEmpty()) return null
            for (k in pool.indices) {
                val line = pool[(startIndex + k) % pool.size]
                if (line !in recent) return line
            }
            return null
        }

        /** Odds per pool: probability, cooldown, and post-warning delay. */
        data class EasterPoolConfig(
            val probability: Float,
            val cooldownMs: Long,
            val delayMs: Long
        )

        fun easterPoolConfig(pool: String): EasterPoolConfig = when (pool) {
            POOL_SPEED_130 -> EasterPoolConfig(0.35f, 5 * 60_000L, 0L)
            POOL_SPEED_140 -> EasterPoolConfig(0.35f, 10 * 60_000L, 0L)
            POOL_SPEED_150 -> EasterPoolConfig(0.15f, 20 * 60_000L, 0L)
            POOL_RPM -> EasterPoolConfig(0.30f, 10 * 60_000L, 4000L)
            POOL_COMBO -> EasterPoolConfig(0.25f, 15 * 60_000L, 4000L)
            POOL_COOLANT_AFTER -> EasterPoolConfig(0.30f, 20 * 60_000L, 5000L)
            POOL_FAULT_NEW -> EasterPoolConfig(0.30f, 15 * 60_000L, 4000L)
            POOL_FAULT_RETURN -> EasterPoolConfig(0.40f, 20 * 60_000L, 4000L)
            POOL_FAULT_SPEEDING -> EasterPoolConfig(0.30f, 15 * 60_000L, 4000L)
            POOL_ULTRA -> EasterPoolConfig(0.02f, 60 * 60_000L, 0L)
            else -> EasterPoolConfig(0f, Long.MAX_VALUE, 0L)
        }

        /**
         * Mutual-exclusion state machine for the two master switches. Turning
         * one assistant ON turns the other OFF; turning one OFF leaves the
         * other unchanged (both may be OFF, never both ON).
         */
        fun resolveAiSwitches(
            toggled: AiSystem,
            checked: Boolean,
            offlineOn: Boolean,
            onlineOn: Boolean
        ): Pair<Boolean, Boolean> = when (toggled) {
            AiSystem.OFFLINE -> if (checked) Pair(true, false) else Pair(false, onlineOn)
            AiSystem.ONLINE -> if (checked) Pair(false, true) else Pair(offlineOn, false)
        }

        /**
         * Online AI must always keep at least one output: killing the last
         * enabled one revives the other instead ([toggledVoice] tells which
         * switch the user just flipped).
         */
        fun enforceOnlineOutput(toggledVoice: Boolean, voiceOn: Boolean, textOn: Boolean): Pair<Boolean, Boolean> =
            when {
                voiceOn || textOn -> Pair(voiceOn, textOn)
                toggledVoice -> Pair(false, true)
                else -> Pair(true, false)
            }

        /**
         * Same at-least-one-output rule for Offline AI: killing the last
         * enabled output revives the other instead.
         */
        fun enforceOfflineOutput(toggledVoice: Boolean, voiceOn: Boolean, textOn: Boolean): Pair<Boolean, Boolean> =
            enforceOnlineOutput(toggledVoice, voiceOn, textOn)
    }

    private var rpmTier = 0
    private var lastRpmValue = 0
    private var coolantAlarmed = false
    private var shiftRpm = PrefsKeys.DEFAULT_SHIFT_RPM
    // Offline AI snapshot (taken when the screen opens; Settings edits rebuild it).
    private var highRpmThreshold = PrefsKeys.DEFAULT_HIGH_RPM
    private var highSpeedThreshold = PrefsKeys.DEFAULT_HIGH_SPEED
    private var coolantThreshold = PrefsKeys.DEFAULT_OFFLINE_COOLANT
    private var offlineVolume = PrefsKeys.DEFAULT_OFFLINE_VOLUME / 100f
    // Display units snapshot (SI stays underneath; only pixels convert).
    private var imperial = false
    private var lastSpeedKmh = 0f
    private var firstSpeedRead = true
    private var firstRpmRead = true
    private var prevCoolantC: Int? = null
    private var connLost = false
    private val offlineKnownCodes = mutableSetOf<String>()
    private var offlineCodesSeeded = false
    private val offlineClearedCodes = mutableSetOf<String>()
    private var easterOn = true
    private var easterLines: Map<String, List<String>> = emptyMap()
    private val easterCooldowns = mutableMapOf<String, Long>()
    private val easterRecent = ArrayDeque<String>()
    private val easterRandom = kotlin.random.Random.Default

    private lateinit var locationManager: LocationManager
    private lateinit var speedView: TubeSpeedometer
    private lateinit var batteryTempView: TextView
    private lateinit var coolantTempView: TextView
    private lateinit var voltageView: TextView
    private lateinit var fuelView: TextView
    private lateinit var edgeFlashView: View
    private lateinit var muteButton: Button
    private var defaultCoolantColor: Int = Color.WHITE
    private lateinit var offlineAiBanner: View
    private lateinit var offlineAiMessage: TextView
    private lateinit var offlineAiTimer: TextView
    private var offlineAiCountdown: CountDownTimer? = null
    private val offlineAiLastFired = mutableMapOf<OfflineAiEvent, Long>()
    private var offlineAiWelcomed = false
    // -- Online AI (independent online assistant) ---------------------------------
    private var onlineAiManager: OnlineAiManager? = null
    private lateinit var onlineAiCard: View
    private lateinit var aiMessage: TextView
    private lateinit var aiTimer: TextView
    private var aiCountdown: CountDownTimer? = null
    private val speechQueue = SpeechQueue()
    private var speakingSeverity: OnlineAiSeverity? = null
    private var onlineAiTts: android.speech.tts.TextToSpeech? = null
    // Optional RPM-driven entertainment hum; reads rpm only, dies with mute.
    private val engineSound = EngineSound()
    private val onlineAiListener = object : OnlineAiManager.Listener {
        override fun onOnlineAiResponse(text: String, severity: OnlineAiSeverity) {
            if (!isAdded) return
            val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, true)) {
                showOnlineAiCard(text)
            }
            if (prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true)) {
                speakOnlineAi(text, severity)
            }
        }
    }

    private val batteryTempReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f)
            batteryTempView.text = getString(R.string.device_temp, temperature)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live_data, container, false)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        obdHelper = (activity as MainActivity).obdHelper

        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        shiftRpm = prefs.getInt(PrefsKeys.SHIFT_RPM, PrefsKeys.DEFAULT_SHIFT_RPM)
            .coerceIn(PrefsKeys.SHIFT_RPM_MIN, PrefsKeys.SHIFT_RPM_MAX)
        highRpmThreshold = prefs.getInt(PrefsKeys.HIGH_RPM_THRESHOLD, PrefsKeys.DEFAULT_HIGH_RPM)
            .coerceIn(PrefsKeys.HIGH_RPM_MIN, PrefsKeys.HIGH_RPM_MAX)
        highSpeedThreshold = prefs.getInt(PrefsKeys.HIGH_SPEED_THRESHOLD, PrefsKeys.DEFAULT_HIGH_SPEED)
            .coerceIn(PrefsKeys.HIGH_SPEED_MIN, PrefsKeys.HIGH_SPEED_MAX)
        coolantThreshold = prefs.getInt(PrefsKeys.OFFLINE_COOLANT_THRESHOLD, PrefsKeys.DEFAULT_OFFLINE_COOLANT)
            .coerceIn(PrefsKeys.OFFLINE_COOLANT_MIN, PrefsKeys.OFFLINE_COOLANT_MAX)
        offlineVolume = prefs.getInt(PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME)
            .coerceIn(0, 100) / 100f
        imperial = Units.isImperial(prefs)
        view.keepScreenOn = prefs.getBoolean(PrefsKeys.KEEP_SCREEN_ON, false)
        rpmTier = 0
        coolantAlarmed = false
        firstSpeedRead = true
        firstRpmRead = true
        prevCoolantC = null
        connLost = false
        offlineKnownCodes.clear()
        offlineCodesSeeded = false
        offlineClearedCodes.clear()
        offlineClearedCodes.clear()
        easterOn = prefs.getBoolean(PrefsKeys.EASTER_EGGS_ENABLED, true)
        easterLines = mapOf(
            POOL_SPEED_130 to resources.getStringArray(R.array.easter_speed_130).toList(),
            POOL_SPEED_140 to resources.getStringArray(R.array.easter_speed_140).toList(),
            POOL_SPEED_150 to resources.getStringArray(R.array.easter_speed_150).toList(),
            POOL_RPM to resources.getStringArray(R.array.easter_rpm_high).toList(),
            POOL_COMBO to resources.getStringArray(R.array.easter_rpm_speed_combo).toList(),
            POOL_COOLANT_AFTER to resources.getStringArray(R.array.easter_coolant_after).toList(),
            POOL_FAULT_NEW to resources.getStringArray(R.array.easter_fault_new).toList(),
            POOL_FAULT_RETURN to resources.getStringArray(R.array.easter_fault_return).toList(),
            POOL_FAULT_SPEEDING to resources.getStringArray(R.array.easter_fault_speeding).toList(),
            POOL_ULTRA to resources.getStringArray(R.array.easter_ultra_rare).toList()
        )
        easterCooldowns.clear()
        easterRecent.clear()

        speedView = view.findViewById<TubeSpeedometer>(R.id.speedView2)
        // Imperial drivers get an mph dial; logic underneath stays km/h.
        speedView.maxSpeed = Units.speedGaugeMax(imperial)
        speedView.unit = Units.speedUnitLabel(imperial)
        val rpmView = view.findViewById<TubeSpeedometer>(R.id.rpmView)
        batteryTempView = view.findViewById<TextView>(R.id.batteryTempView)
        coolantTempView = view.findViewById<TextView>(R.id.coolantTempView)
        voltageView = view.findViewById<TextView>(R.id.voltageView)
        fuelView = view.findViewById<TextView>(R.id.fuelView)
        edgeFlashView = view.findViewById(R.id.edgeFlashView)
        muteButton = view.findViewById(R.id.button_mute)
        defaultCoolantColor = coolantTempView.currentTextColor
        offlineAiBanner = view.findViewById(R.id.offlineAiBanner)
        offlineAiMessage = view.findViewById(R.id.offlineAiMessage)
        offlineAiTimer = view.findViewById(R.id.offlineAiTimer)
        offlineAiCountdown?.cancel()
        offlineAiCountdown = null
        offlineAiLastFired.clear()
        offlineAiWelcomed = false
        setupMuteButton()
        view.findViewById<Button>(R.id.offlineAiSkip).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            skipOfflineAi()
        }

        // RPM dial zones (x100 scale, max 50) arranged around the shift point.
        // The readout text follows the needle's zone automatically.
        // NOTE: Gauge ships with 3 default sections and addSections() APPENDS,
        // so clearSections() must come first — otherwise our first section
        // (index 3) conflicts with the last default one and the gauge throws.
        val rpmMax = rpmView.maxSpeed
        rpmView.clearSections()
        rpmSections(rpmMax, shiftRpm).forEach { (start, end, color) ->
            rpmView.addSections(
                com.github.anastr.speedviewlib.components.Section(start, end, color)
            )
        }
        rpmView.speedTextColor = rpmZoneColor(0, shiftRpm)
        rpmView.onSectionChangeListener = { _, newSection ->
            rpmView.speedTextColor = newSection?.color ?: rpmView.speedTextColor
        }

        val speedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)

        if (speedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
            rpmView.visibility = View.GONE
            batteryTempView.visibility = View.VISIBLE
            coolantTempView.visibility = View.GONE
            voltageView.visibility = View.GONE
            fuelView.visibility = View.GONE

            val constraintLayout = view as ConstraintLayout
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.connect(R.id.speedView2, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            // Landscape/tablet layouts park the speedo in the left pane next to
            // the readings; portrait centers it full-width instead.
            if (view.findViewById<View>(R.id.guideline_v) != null) {
                constraintSet.connect(R.id.speedView2, ConstraintSet.END, R.id.guideline_v, ConstraintSet.START)
            } else {
                constraintSet.connect(R.id.speedView2, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            }
            constraintSet.applyTo(constraintLayout)

            locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1f, this)
            } catch (e: SecurityException) {
                Log.e("LiveDataFragment", "Location permission missing for GPS speed", e)
                ErrorDialogFragment.newInstance(getString(R.string.live_data_gps_permission))
                    .show(parentFragmentManager, "errorDialog")
                return
            }
            androidx.core.content.ContextCompat.registerReceiver(
                requireContext(),
                batteryTempReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            fireOfflineAiOnce(OfflineAiEvent.WELCOME, getString(R.string.offline_ai_online))
        } else {
            batteryTempView.visibility = View.GONE
            coolantTempView.visibility = View.VISIBLE
            voltageView.visibility = View.VISIBLE
            fuelView.visibility = View.VISIBLE
            if (obdHelper.demoMode) {
                setupDemoSliders(view)
            }
            fireOfflineAiOnce(OfflineAiEvent.WELCOME, getString(R.string.offline_ai_online))
            onlineAiCard = view.findViewById(R.id.onlineAiCard)
            aiMessage = view.findViewById(R.id.aiMessage)
            aiTimer = view.findViewById(R.id.aiTimer)
            view.findViewById<Button>(R.id.onlineAiSkip).setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                skipOnlineAi()
            }
            onlineAiManager = OnlineAiManager(
                requireContext(),
                (activity as MainActivity).aiService,
                onlineAiListener
            )
            // One hint per screen when the brain cannot generate — otherwise
            // the silence is a mystery. Not repeated, auto-dismisses.
            if (onlineAiEnabled() && onlineAiManager?.isBrainConfigured() == false) {
                showOnlineAiCard(getString(R.string.ai_no_key_hint))
            }
            lifecycleScope.launch {
                obdHelper.startLiveDataMonitoring()
            }
            maybeStartEngineSound()
            lifecycleScope.launch {
                // Slow telemetry: Online AI poll + Offline AI fault tracking.
                // Polls once immediately (drive-start heads-up) then ~every 30 s.
                suspend fun pollSlow() {
                    val slow = runCatching { obdHelper.readSlowTelemetry() }.getOrNull() ?: return
                    runCatching { onlineAiManager?.onSlowPoll(slow) }
                    // Publish only successful reads; "--" placeholders mean unknown.
                    if (slow.voltageV != null) {
                        ObdDataHolder.voltageFlow.value = "${slow.voltageV} V"
                    }
                    if (slow.fuelPct != null) {
                        ObdDataHolder.fuelFlow.value = "${slow.fuelPct} %"
                    }
                    if (!offlineCodesSeeded) {
                        offlineCodesSeeded = true
                        offlineKnownCodes.addAll(slow.codes)
                    } else {
                        val fresh = slow.codes.filter { it !in offlineKnownCodes }
                        val gone = offlineKnownCodes.filter { it !in slow.codes }
                        offlineClearedCodes.addAll(gone)
                        offlineKnownCodes.retainAll(slow.codes.toSet())
                        offlineKnownCodes.addAll(slow.codes)
                        val returned = fresh.filter { it in offlineClearedCodes }
                        offlineClearedCodes.removeAll(returned.toSet())
                        val brandNew = fresh - returned.toSet()
                        if (brandNew.isNotEmpty() && alertEnabled(PrefsKeys.NEW_FAULT_ENABLED)) {
                            fireOfflineAi(
                                OfflineAiEvent.NEW_FAULT_CODE,
                                offlineAiText(
                                    OfflineAiEvent.NEW_FAULT_CODE,
                                    brandNew.joinToString(", ")
                                ),
                                OFFLINE_AI_EVENT_COOLDOWN_MS
                            )
                            if (lastSpeedKmh >= highSpeedThreshold) {
                                maybeFireEasterEgg(POOL_FAULT_SPEEDING)
                            } else {
                                maybeFireEasterEgg(POOL_FAULT_NEW)
                            }
                        }
                        if (returned.isNotEmpty()) {
                            maybeFireEasterEgg(POOL_FAULT_RETURN)
                        }
                    }
                }
                pollSlow()
                while (ObdDataHolder.isMonitoring.get()) {
                    delay(OnlineAiManager.SLOW_POLL_MS)
                    if (!isAdded) break
                    runCatching { pollSlow() }
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.speedFlow.collect { speedString ->
                    val speedValue = speedString.split(" ")[0].toFloatOrNull() ?: 0f
                    // Needle converts; every threshold below stays km/h.
                    speedView.speedTo(Units.displaySpeed(speedValue.toDouble(), imperial).toFloat(), 400)
                    onlineAiManager?.onSpeed(speedValue)
                    val prevSpeed = lastSpeedKmh
                    lastSpeedKmh = speedValue
                    if (!firstSpeedRead &&
                        alertEnabled(PrefsKeys.HIGH_SPEED_ENABLED) &&
                        prevSpeed < highSpeedThreshold &&
                        speedValue >= highSpeedThreshold
                    ) {
                        fireOfflineAi(
                            OfflineAiEvent.HIGH_SPEED,
                            offlineAiText(OfflineAiEvent.HIGH_SPEED),
                            OFFLINE_AI_EVENT_COOLDOWN_MS
                        )
                    }
                    if (!firstSpeedRead) {
                        val tier = speedEasterTier(speedValue)
                        if (tier > 0 && tier > speedEasterTier(prevSpeed)) {
                            maybeFireEasterEgg(poolForSpeedTier(tier))
                        }
                        maybeComboEaster(lastRpmValue)
                    }
                    firstSpeedRead = false
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.rpmFlow.collect { rpmString ->
                    if (rpmString == "ERROR") {
                        if (!connLost) {
                            connLost = true
                            if (alertEnabled(PrefsKeys.CONN_LOST_ENABLED)) {
                                fireOfflineAi(
                                    OfflineAiEvent.CONNECTION_LOST,
                                    offlineAiText(OfflineAiEvent.CONNECTION_LOST),
                                    OFFLINE_AI_EVENT_COOLDOWN_MS
                                )
                            }
                        }
                    } else if (connLost) {
                        connLost = false
                        if (alertEnabled(PrefsKeys.CONN_RESTORED_ENABLED)) {
                            fireOfflineAi(
                                OfflineAiEvent.CONNECTION_RESTORED,
                                offlineAiText(OfflineAiEvent.CONNECTION_RESTORED),
                                OFFLINE_AI_EVENT_COOLDOWN_MS
                            )
                        }
                    }
                    val rpmValue = rpmString.split(" ")[0].toIntOrNull() ?: 0
                    val prevRpm = lastRpmValue
                    lastRpmValue = rpmValue
                    engineSound.rpm = rpmValue
                    rpmView.speedTo(rpmValue.toFloat() / 100, 400)
                    flashEdges(rpmZoneColor(rpmValue, shiftRpm))
                    onlineAiManager?.onRpm(rpmValue, shiftRpm)
                    if (!firstRpmRead) {
                        if (alertEnabled(PrefsKeys.ENGINE_START_ENABLED) &&
                            shiftCrossed(prevRpm, rpmValue, 500)
                        ) {
                            fireOfflineAi(
                                OfflineAiEvent.ENGINE_STARTED,
                                offlineAiText(OfflineAiEvent.ENGINE_STARTED),
                                OFFLINE_AI_EVENT_COOLDOWN_MS
                            )
                        }
                        if (alertEnabled(PrefsKeys.ENGINE_STOP_ENABLED) &&
                            prevRpm > 500 && crossedDown(prevRpm, rpmValue, 400)
                        ) {
                            fireOfflineAi(
                                OfflineAiEvent.ENGINE_STOPPED,
                                offlineAiText(OfflineAiEvent.ENGINE_STOPPED),
                                OFFLINE_AI_EVENT_COOLDOWN_MS
                            )
                        }
                        if (alertEnabled(PrefsKeys.SHIFT_POINT_ENABLED) &&
                            shiftCrossed(prevRpm, rpmValue, amberThreshold(shiftRpm))
                        ) {
                            fireOfflineAi(
                                OfflineAiEvent.SHIFT_POINT,
                                offlineAiText(OfflineAiEvent.SHIFT_POINT),
                                OFFLINE_AI_SHIFT_COOLDOWN_MS
                            )
                        }
                        if (alertEnabled(PrefsKeys.HIGH_RPM_ENABLED) &&
                            shiftCrossed(prevRpm, rpmValue, highRpmThreshold)
                        ) {
                            fireOfflineAi(
                                OfflineAiEvent.HIGH_RPM,
                                offlineAiText(OfflineAiEvent.HIGH_RPM),
                                OFFLINE_AI_EVENT_COOLDOWN_MS
                            )
                            maybeFireEasterEgg(POOL_RPM)
                            maybeComboEaster(rpmValue)
                        }
                    }
                    firstRpmRead = false

                    val newTier = nextRpmTier(rpmValue, rpmTier, shiftRpm)
                    if (newTier > rpmTier) {
                        when (newTier) {
                            4 -> {
                                playSound(R.raw.danger)
                                rpmView.speedTextColor = Color.RED
                                Handler(Looper.getMainLooper()).postDelayed({
                                    // Back to the zone color, not a stale default.
                                    rpmView.speedTextColor = rpmZoneColor(lastRpmValue, shiftRpm)
                                }, 500)
                            }
                            3 -> playSound(R.raw.thirty)
                            2 -> playSound(R.raw.twenty)
                            1 -> playSound(R.raw.ten)
                        }
                    } else if (newTier < rpmTier) {
                        // RPM fell back below the warning band: cut the sound off
                        // instead of letting it play to the end.
                        stopSound()
                    }
                    rpmTier = newTier
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.coolantTempFlow.collect { coolantTempString ->
                    val coolantC = coolantTempString.split(" ")[0].toIntOrNull()
                    val coolantShown = coolantC?.let { Units.displayTemp(it.toDouble(), imperial).toInt() }
                    coolantTempView.text = getString(
                        R.string.coolant_value,
                        if (imperial && coolantShown != null) "$coolantShown °F" else coolantTempString
                    )
                    onlineAiManager?.onCoolant(coolantC)
                    if (prevCoolantC != null &&
                        alertEnabled(PrefsKeys.OFFLINE_COOLANT_ENABLED) &&
                        shiftCrossed(prevCoolantC ?: 0, coolantC ?: 0, coolantThreshold)
                    ) {
                        fireOfflineAi(
                            OfflineAiEvent.COOLANT_HIGH,
                            offlineAiText(OfflineAiEvent.COOLANT_HIGH),
                            OFFLINE_AI_EVENT_COOLDOWN_MS
                        )
                    }
                    prevCoolantC = coolantC
                    val shouldAlarm = nextCoolantAlarmed(coolantC, coolantAlarmed)
                    if (shouldAlarm && !coolantAlarmed) {
                        flashEdges(RPM_RED, strong = true)
                        if (offlineMasterOn()) {
                            playSound(R.raw.danger)
                            val warning = if (imperial) {
                                getString(
                                    R.string.live_data_coolant_warning_f,
                                    Units.displayTemp((coolantC ?: 0).toDouble(), true).toInt()
                                )
                            } else {
                                getString(R.string.live_data_coolant_warning, coolantC ?: 0)
                            }
                            fireOfflineAi(OfflineAiEvent.COOLANT_HIGH, warning)
                        }
                    } else if (!shouldAlarm && coolantAlarmed) {
                        // Temperature is back to normal: cut the danger siren
                        // so the recovery message is heard, not drowned out.
                        stopSound()
                        fireOfflineAi(
                            OfflineAiEvent.COOLANT_OK,
                            getString(R.string.offline_ai_coolant_ok)
                        )
                        maybeFireEasterEgg(POOL_COOLANT_AFTER)
                    }
                    coolantAlarmed = shouldAlarm
                    coolantTempView.setTextColor(
                        if (coolantAlarmed) Color.RED else defaultCoolantColor
                    )
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.engineLoadFlow.collect { loadString ->
                    onlineAiManager?.onEngineLoad(loadString.split(" ")[0].toIntOrNull())
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.voltageFlow.collect { voltageString ->
                    voltageView.text = getString(R.string.voltage_value, voltageString)
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.fuelFlow.collect { fuelString ->
                    fuelView.text = getString(R.string.fuel_value, fuelString)
                }
            }
        }
    }

    override fun onLocationChanged(location: android.location.Location) {
        val speedKmh = location.speed * 3.6f
        speedView.speedTo(speedKmh)
        val prevSpeed = lastSpeedKmh
        lastSpeedKmh = speedKmh
        if (!firstSpeedRead &&
            alertEnabled(PrefsKeys.HIGH_SPEED_ENABLED) &&
            prevSpeed < highSpeedThreshold &&
            speedKmh >= highSpeedThreshold
        ) {
            runCatching {
                fireOfflineAi(
                    OfflineAiEvent.HIGH_SPEED,
                    offlineAiText(OfflineAiEvent.HIGH_SPEED),
                    OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
        }
        if (!firstSpeedRead) {
            val tier = speedEasterTier(speedKmh)
            if (tier > 0 && tier > speedEasterTier(prevSpeed)) {
                runCatching { maybeFireEasterEgg(poolForSpeedTier(tier)) }
            }
            runCatching { maybeComboEaster(lastRpmValue) }
        }
        firstSpeedRead = false
    }

    override fun onProviderDisabled(provider: String) {}

    override fun onProviderEnabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    /** Demo mode: the user drives the gauges with sliders — no simulated loop. */
    private fun setupDemoSliders(view: View) {
        view.findViewById<View>(R.id.demoPanel).visibility = View.VISIBLE
        val (seedSpeed, seedRpm, seedCoolant) = DemoObdSource.liveValuesAt(0)

        val speedLabel = view.findViewById<TextView>(R.id.demoSpeedLabel)
        val rpmLabel = view.findViewById<TextView>(R.id.demoRpmLabel)
        val coolantLabel = view.findViewById<TextView>(R.id.demoCoolantLabel)

        fun pushSpeed(v: Int) {
            speedLabel.text = if (imperial) {
                getString(R.string.demo_speed_mph, Units.kmhToMph(v.toDouble()).toInt())
            } else {
                getString(R.string.demo_speed_label, v)
            }
            ObdDataHolder.speedFlow.value = "$v Km/h"
        }
        fun pushRpm(v: Int) {
            rpmLabel.text = getString(R.string.demo_rpm_label, v)
            ObdDataHolder.rpmFlow.value = "$v RPM"
            // Demo has no load slider: derive a plausible load from the RPM.
            val load = (10 + (v - RPM_SLIDER_OFFSET) * 85 / 4200).coerceIn(5, 99)
            ObdDataHolder.engineLoadFlow.value = "$load %"
        }
        fun pushCoolant(v: Int) {
            coolantLabel.text = if (imperial) {
                getString(R.string.demo_coolant_f, Units.cToF(v.toDouble()).toInt())
            } else {
                getString(R.string.demo_coolant_label, v)
            }
            ObdDataHolder.coolantTempFlow.value = "$v °C"
        }

        val speedSlider = view.findViewById<SeekBar>(R.id.demoSpeedSlider)
        val rpmSlider = view.findViewById<SeekBar>(R.id.demoRpmSlider)
        val coolantSlider = view.findViewById<SeekBar>(R.id.demoCoolantSlider)
        speedSlider.setOnSeekBarChangeListener(sliderListener(::pushSpeed))
        rpmSlider.setOnSeekBarChangeListener(sliderListener { pushRpm(it + RPM_SLIDER_OFFSET) })
        coolantSlider.setOnSeekBarChangeListener(sliderListener { pushCoolant(it + COOLANT_SLIDER_OFFSET) })

        speedSlider.progress = seedSpeed
        rpmSlider.progress = (seedRpm - RPM_SLIDER_OFFSET).coerceAtLeast(0)
        coolantSlider.progress = (seedCoolant - COOLANT_SLIDER_OFFSET).coerceAtLeast(0)
        pushSpeed(seedSpeed)
        pushRpm(seedRpm)
        pushCoolant(seedCoolant)
    }

    private fun sliderListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun isMuted(): Boolean =
        requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PrefsKeys.MUTE_SOUND, false)

    private fun onlineAiEnabled(): Boolean =
        requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)

    private fun setupMuteButton() {
        refreshMuteLabel()
        muteButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val prefs = requireActivity()
                .getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            val muted = !prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)
            prefs.edit { putBoolean(PrefsKeys.MUTE_SOUND, muted) }
            refreshMuteLabel()
            if (muted) {
                stopSound()
                engineSound.stop()
                tts?.stop()
                stopSpeechPlayback()
            } else {
                maybeStartEngineSound()
            }
        }
    }

    /** Starts the engine hum when enabled, unmuted and on screen; else stops it. */
    private fun maybeStartEngineSound() {
        if (!isAdded) return
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, false) && !isMuted()) {
            engineSound.start()
        } else {
            engineSound.stop()
        }
    }

    private fun refreshMuteLabel() {
        if (!::muteButton.isInitialized) return
        muteButton.text = getString(
            if (isMuted()) R.string.live_data_unmute else R.string.live_data_mute
        )
    }

    /** Pulses a colored border around the screen; green rests faint, amber/red flash. */
    private fun flashEdges(color: Int, strong: Boolean = false) {
        if (!::edgeFlashView.isInitialized) return
        val stroke = (14 * resources.displayMetrics.density).toInt()
        edgeFlashView.background =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(stroke, color)
            }
        edgeFlashView.visibility = View.VISIBLE
        edgeFlashView.animate().cancel()
        if (color == RPM_GREEN && !strong) {
            edgeFlashView.alpha = 0.25f
        } else {
            edgeFlashView.alpha = 1f
            edgeFlashView.animate().alpha(0.15f).setDuration(450).start()
        }
    }

    /** Speaks [text] with TTS at Offline AI volume, initializing on first use. */
    private fun speakText(text: String, utterance: String = "offlineAi") {
        val params = Bundle().apply {
            putFloat(
                android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME,
                offlineVolume
            )
        }
        val engine = tts
        if (engine == null) {
            // First use: speak from the init callback once the engine is ready.
            tts = android.speech.tts.TextToSpeech(requireContext()) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    runCatching { tts?.language = java.util.Locale.getDefault() }
                    tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, utterance)
                } else {
                    Log.e("LiveDataFragment", "TTS init failed: $status")
                }
            }
        } else {
            engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, utterance)
        }
    }

    private fun offlinePrefs() =
        requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    /** Master switch: the whole Offline AI system is dead when this is off. */
    private fun offlineMasterOn(): Boolean =
        offlinePrefs().getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true)

    private fun offlineVoiceSwitchOn(): Boolean =
        offlinePrefs().getBoolean(PrefsKeys.VOICE_INSIGHT, true)

    private fun offlineTextSwitchOn(): Boolean =
        offlinePrefs().getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)

    private fun alertEnabled(key: String, def: Boolean = true): Boolean =
        offlinePrefs().getBoolean(key, def)

    /** Event → display text, shown on the banner and spoken with device TTS. */
    private fun offlineAiText(event: OfflineAiEvent, detail: String = ""): String = when (event) {
        OfflineAiEvent.WELCOME -> getString(R.string.offline_ai_online)
        OfflineAiEvent.SHIFT_POINT -> getString(R.string.offline_ai_shift_up)
        OfflineAiEvent.HIGH_RPM -> getString(R.string.offline_ai_high_rpm)
        OfflineAiEvent.HIGH_SPEED -> getString(R.string.offline_ai_high_speed)
        OfflineAiEvent.COOLANT_HIGH -> getString(R.string.offline_ai_coolant_high)
        OfflineAiEvent.COOLANT_OK -> getString(R.string.offline_ai_coolant_ok)
        OfflineAiEvent.NEW_FAULT_CODE ->
            if (detail.isBlank()) getString(R.string.offline_ai_new_fault)
            else "${getString(R.string.offline_ai_new_fault)} $detail"
        OfflineAiEvent.CONNECTION_LOST -> getString(R.string.offline_ai_conn_lost)
        OfflineAiEvent.CONNECTION_RESTORED -> getString(R.string.offline_ai_conn_restored)
        OfflineAiEvent.ENGINE_STARTED -> getString(R.string.offline_ai_engine_start)
        OfflineAiEvent.ENGINE_STOPPED -> getString(R.string.offline_ai_engine_stop)
    }

    /**
     * Fires an Offline AI cue. Callers check the per-event enable switch and
     * threshold edge; here the master switch and the voice/text outputs are
     * applied. Events without a [cooldownMs] gate (0) fire on every edge.
     */
    private fun fireOfflineAi(event: OfflineAiEvent, text: String, cooldownMs: Long = 0L) {
        if (!offlineMasterOn()) return
        if (cooldownMs > 0) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (!offlineAiReady(offlineAiLastFired[event] ?: 0L, now, cooldownMs)) return
            offlineAiLastFired[event] = now
        }
        emitOfflineAiCue(text)
    }

    /** One-shot cue per screen (e.g. the "ready" greeting). */
    private fun fireOfflineAiOnce(event: OfflineAiEvent, text: String) {
        if (offlineAiWelcomed) return
        offlineAiWelcomed = true
        if (!offlineMasterOn()) return
        offlineAiLastFired[event] = android.os.SystemClock.elapsedRealtime()
        emitOfflineAiCue(text)
    }

    /** Voice + banner fan-out shared by normal cues and Easter eggs. */
    private fun emitOfflineAiCue(text: String) {
        if (offlineVoiceSwitchOn() && !isMuted()) {
            speakText(text)
        }
        if (offlineTextSwitchOn()) showOfflineAiBanner(text)
    }

    /**
     * Easter-egg gate (Offline AI only, never Online AI). The normal warning
     * for this edge has already fired (or the edge has none); this only adds
     * an occasional joke: master + egg switches, per-pool probability and
     * cooldown, no-repeat tracking, and a short delay so the serious message
     * speaks first. A tiny global ultra-rare roll may substitute the pick.
     */
    private fun maybeFireEasterEgg(pool: String) {
        if (!easterOn) return
        val lines = easterLines[pool].orEmpty()
        val config = easterPoolConfig(pool)
        if (lines.isEmpty() || config.probability <= 0f) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!offlineAiReady(easterCooldowns[pool] ?: 0L, now, config.cooldownMs)) return
        if (!rollEasterEgg(easterRandom.nextFloat(), config.probability)) return
        val ultraLines = easterLines[POOL_ULTRA].orEmpty()
        val ultraConfig = easterPoolConfig(POOL_ULTRA)
        val useUltra = ultraLines.isNotEmpty() &&
            offlineAiReady(easterCooldowns[POOL_ULTRA] ?: 0L, now, ultraConfig.cooldownMs) &&
            rollEasterEgg(easterRandom.nextFloat(), ultraConfig.probability)
        val finalLines = if (useUltra) ultraLines else lines
        val line = pickFreshLine(finalLines, easterRecent, easterRandom.nextInt(finalLines.size))
            ?: return
        easterCooldowns[pool] = now
        if (useUltra) easterCooldowns[POOL_ULTRA] = now
        easterRecent.addLast(line)
        while (easterRecent.size > EASTER_RECENT_MAX) easterRecent.removeFirst()
        val delayMs = if (useUltra) ultraConfig.delayMs else config.delayMs
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAdded) return@postDelayed
            if (!easterEnabledPref() || !offlineMasterOn()) return@postDelayed
            emitOfflineAiCue(line)
        }, delayMs)
    }

    private fun easterEnabledPref(): Boolean =
        requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PrefsKeys.EASTER_EGGS_ENABLED, true)

    /** Combo check shared by the RPM and speed edges (needs both thresholds). */
    private fun maybeComboEaster(rpmValue: Int) {
        if (comboActive(rpmValue, highRpmThreshold, lastSpeedKmh, highSpeedThreshold)) {
            maybeFireEasterEgg(POOL_COMBO)
        }
    }

    /** Small bottom popup with a word-count countdown; replaces itself when re-fired. */
    private fun showOfflineAiBanner(text: String) {
        if (!::offlineAiBanner.isInitialized) return
        offlineAiMessage.text = text
        offlineAiBanner.visibility = View.VISIBLE
        offlineAiCountdown?.cancel()
        val durationMs = bannerDurationMs(text)
        offlineAiTimer.text = getString(R.string.countdown_value, (durationMs / 1000).toInt())
        offlineAiCountdown = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                offlineAiTimer.text = getString(R.string.countdown_value, ((millisUntilFinished + 999) / 1000).toInt())
            }

            override fun onFinish() {
                if (::offlineAiBanner.isInitialized) offlineAiBanner.visibility = View.GONE
            }
        }.also { it.start() }
    }

    /** Dismisses the Offline AI banner early and cuts its voice. */
    private fun skipOfflineAi() {
        offlineAiCountdown?.cancel()
        offlineAiCountdown = null
        if (::offlineAiBanner.isInitialized) offlineAiBanner.visibility = View.GONE
        stopSound()
        tts?.stop()
    }

    /** Online AI response card (bottom): linger matches the message length. */
    private fun showOnlineAiCard(text: String) {
        if (!::onlineAiCard.isInitialized) return
        aiMessage.text = text
        onlineAiCard.visibility = View.VISIBLE
        aiCountdown?.cancel()
        val durationMs = bannerDurationMs(text)
        aiTimer.text = getString(R.string.countdown_value, (durationMs / 1000).toInt())
        aiCountdown = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                aiTimer.text = getString(R.string.countdown_value, ((millisUntilFinished + 999) / 1000).toInt())
            }

            override fun onFinish() {
                if (::onlineAiCard.isInitialized) onlineAiCard.visibility = View.GONE
            }
        }.also { it.start() }
    }

    /** Dismisses the Online AI card early and drops its queued voice. */
    private fun skipOnlineAi() {
        aiCountdown?.cancel()
        aiCountdown = null
        if (::onlineAiCard.isInitialized) onlineAiCard.visibility = View.GONE
        speechQueue.clear()
        speakingSeverity = null
        stopSpeechPlayback()
    }

    private fun aiVolume(): Float {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100) / 100f
    }

    /**
     * Speaks an Online AI reply with the device voice. Never throws; the dashboard always survives.
     */
    private fun speakOnlineAi(text: String, severity: OnlineAiSeverity) {
        if (isMuted()) return // card already shown; voice stays silent
        val utterance = QueuedSpeech(text, severity)
        val current = speakingSeverity
        if (current != null) {
            if (severity.ordinal > current.ordinal) {
                // Higher severity preempts: drop the current remainder.
                stopSpeechPlayback()
                speakingSeverity = null
                lifecycleScope.launch { playSpeech(utterance) }
            } else {
                speechQueue.offer(utterance)
            }
        } else {
            lifecycleScope.launch { playSpeech(utterance) }
        }
    }

    private fun playSpeech(utterance: QueuedSpeech) {
        speakingSeverity = utterance.severity
        if (!isAdded) {
            speakingSeverity = null
            return
        }
        playDeviceVoice(utterance.text)
    }

    /** Device voice on its own engine so Offline AI cues can never flush it. */
    private fun playDeviceVoice(text: String) {
        val params = Bundle().apply {
            putFloat(
                android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME,
                aiVolume()
            )
        }
        val engine = onlineAiTts
        if (engine == null) {
            onlineAiTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    onlineAiTts?.setOnUtteranceProgressListener(speechProgressListener)
                    runCatching { onlineAiTts?.language = java.util.Locale.getDefault() }
                    onlineAiTts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "onlineai")
                } else {
                    Log.e("LiveDataFragment", "Online AI TTS init failed: $status")
                    Handler(Looper.getMainLooper()).post { onSpeechDone() }
                }
            }
        } else {
            engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "onlineai")
        }
    }

    private val speechProgressListener = object : android.speech.tts.UtteranceProgressListener() {
        override fun onDone(utteranceId: String?) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onError(utteranceId: String?) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?, errorCode: Int) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onStart(utteranceId: String?) {}
    }

    private fun onSpeechDone() {
        speakingSeverity = null
        val next = speechQueue.poll()
        if (next != null && isAdded) {
            lifecycleScope.launch { playSpeech(next) }
        }
    }

    /** Stops whatever the Online AI is saying (preemption, mute, teardown). */
    private fun stopSpeechPlayback() {
        runCatching { onlineAiTts?.stop() }
    }

    private fun playSound(soundResId: Int) {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
            return
        }

        // release() only — stop() on an already-released player throws IllegalStateException
        mediaPlayer?.release()
        mediaPlayer = null
        mediaPlayer = MediaPlayer.create(context, soundResId)?.apply {
            setVolume(offlineVolume, offlineVolume)
            start()
            setOnCompletionListener {
                mediaPlayer = null
                it.release()
            }
        }
    }

    /** Cuts off a warning that is still playing (e.g. RPM fell back down). */
    private fun stopSound() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        offlineAiCountdown?.cancel()
        offlineAiCountdown = null
        aiCountdown?.cancel()
        aiCountdown = null
        onlineAiManager?.stop()
        onlineAiManager = null
        speechQueue.clear()
        speakingSeverity = null
        stopSpeechPlayback()
        onlineAiTts?.shutdown()
        onlineAiTts = null
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(this)
        }
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val speedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)
        if (speedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
            requireContext().unregisterReceiver(batteryTempReceiver)
        }
        // Rotation recreates the view but keeps the process (and the socket):
        // only tear the connection down when truly leaving the screen.
        if (activity?.isChangingConfigurations != true) {
            obdHelper.disconnectFromObdDevice()
            obdHelper.stopLiveDataMonitoring()
        }
        stopSound()
        engineSound.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
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

        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

        val radioGroupTheme = view.findViewById<RadioGroup>(R.id.radioGroupTheme)
        when ((activity as MainActivity).currentThemeMode(prefs)) {
            ThemeMode.LIGHT -> radioGroupTheme.check(R.id.radioThemeLight)
            ThemeMode.DARK -> radioGroupTheme.check(R.id.radioThemeDark)
            ThemeMode.SYSTEM -> radioGroupTheme.check(R.id.radioThemeSystem)
        }
        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioThemeLight -> ThemeMode.LIGHT
                R.id.radioThemeDark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            prefs.edit {
                putString(PrefsKeys.THEME_MODE, mode.prefValue)
            }
            (activity as MainActivity).applyThemeMode(mode)
        }

        val apiKeyEditText = view.findViewById<TextInputEditText>(R.id.apiKeyEditText)
        val apiKeyLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.apiKeyLayout)
        val modelIdEditText = view.findViewById<TextInputEditText>(R.id.modelIdEditText)
        val baseUrlEditText = view.findViewById<TextInputEditText>(R.id.baseUrlEditText)
        val baseUrlLayout = view.findViewById<View>(R.id.baseUrlLayout)
        val providerSpinner = view.findViewById<Spinner>(R.id.providerSpinner)
        val saveButton = view.findViewById<Button>(R.id.saveButton)

        val providers = AiProvider.entries
        providerSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            providers.map { it.displayName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val savedProvider = AiProvider.fromId(prefs.getString(PrefsKeys.AI_PROVIDER, null))
        var lastProvider = savedProvider
        fun renderProvider(selected: AiProvider) {
            baseUrlLayout.visibility = if (selected.showBaseUrl) View.VISIBLE else View.GONE
            apiKeyLayout.hint = getString(
                if (selected.needsKey) R.string.settings_api_key_hint
                else R.string.settings_api_key_hint_custom
            )
        }
        providerSpinner.setSelection(providers.indexOf(savedProvider))
        renderProvider(savedProvider)
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, itemView: View?, position: Int, id: Long) {
                val selected = providers[position]
                renderProvider(selected)
                // Suggest this provider's model: fill when blank, or swap out the
                // previous provider's default — a hand-typed custom model is kept.
                val current = modelIdEditText.text?.toString().orEmpty()
                if (current.isBlank() || current == lastProvider.defaultModel) {
                    modelIdEditText.setText(selected.defaultModel)
                }
                lastProvider = selected
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        apiKeyEditText.setText(prefs.getString(PrefsKeys.OPENAI_API_KEY, ""))
        val savedModel = prefs.getString(PrefsKeys.OPENAI_MODEL_ID, "").orEmpty()
        modelIdEditText.setText(savedModel.ifEmpty { savedProvider.defaultModel })
        baseUrlEditText.setText(prefs.getString(PrefsKeys.AI_BASE_URL, ""))

        val switchMuteSound = view.findViewById<SwitchMaterial>(R.id.switchMuteSound)

        switchMuteSound.isChecked = prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)

        switchMuteSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean(PrefsKeys.MUTE_SOUND, isChecked)
            }
        }

        val radioGroupUnits = view.findViewById<RadioGroup>(R.id.radioGroupUnits)
        if (Units.isImperial(prefs)) {
            radioGroupUnits.check(R.id.radioUnitsImperial)
        } else {
            radioGroupUnits.check(R.id.radioUnitsMetric)
        }
        radioGroupUnits.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit {
                putString(
                    PrefsKeys.UNITS,
                    if (checkedId == R.id.radioUnitsImperial) PrefsKeys.UNITS_IMPERIAL
                    else PrefsKeys.UNITS_METRIC
                )
            }
        }

        val switchKeepScreen = view.findViewById<SwitchMaterial>(R.id.switchKeepScreen)
        switchKeepScreen.isChecked = prefs.getBoolean(PrefsKeys.KEEP_SCREEN_ON, false)
        switchKeepScreen.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(PrefsKeys.KEEP_SCREEN_ON, isChecked) }
        }

        val switchEngineSound = view.findViewById<SwitchMaterial>(R.id.switchEngineSound)
        switchEngineSound.isChecked = prefs.getBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, false)
        switchEngineSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, isChecked) }
        }

        val languages = AppLanguage.entries
        val languageSpinner = view.findViewById<Spinner>(R.id.languageSpinner)
        languageSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            languages.map { it.displayName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        languageSpinner.setSelection(
            languages.indexOf(AppLanguage.fromTag(prefs.getString(PrefsKeys.APP_LANGUAGE, null))).coerceAtLeast(0)
        )
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, itemView: View?, position: Int, id: Long) {
                val tag = languages[position].tag
                if (prefs.getString(PrefsKeys.APP_LANGUAGE, null) != tag) {
                    prefs.edit { putString(PrefsKeys.APP_LANGUAGE, tag) }
                    (activity as MainActivity).applyAppLanguage(tag)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val radioGroupSpeedSource = view.findViewById<RadioGroup>(R.id.radioGroupSpeedSource)
        val savedSpeedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)
        if (savedSpeedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
            radioGroupSpeedSource.check(R.id.radioButtonGps)
        } else {
            radioGroupSpeedSource.check(R.id.radioButtonObd2)
        }

        // -- Offline AI section: master switch, outputs, volume, event alerts ----
        val switchOfflineAi = view.findViewById<SwitchMaterial>(R.id.switchOfflineAi)
        val offlineAiContent = view.findViewById<View>(R.id.offlineAiContent)
        val switchOfflineVoice = view.findViewById<SwitchMaterial>(R.id.switchOfflineVoice)
        val switchOfflineText = view.findViewById<SwitchMaterial>(R.id.switchOfflineText)
        val offlineVolumeValue = view.findViewById<TextView>(R.id.offlineVolumeValue)
        val offlineVolumeSeek = view.findViewById<SeekBar>(R.id.offlineVolumeSeek)
        val switchShiftPoint = view.findViewById<SwitchMaterial>(R.id.switchShiftPoint)
        val shiftValue = view.findViewById<TextView>(R.id.shiftRpmValue)
        val shiftSeek = view.findViewById<SeekBar>(R.id.shiftRpmSeekBar)
        val switchHighRpm = view.findViewById<SwitchMaterial>(R.id.switchHighRpm)
        val highRpmValue = view.findViewById<TextView>(R.id.highRpmValue)
        val highRpmSeek = view.findViewById<SeekBar>(R.id.highRpmSeekBar)
        val switchHighSpeed = view.findViewById<SwitchMaterial>(R.id.switchHighSpeed)
        val highSpeedValue = view.findViewById<TextView>(R.id.highSpeedValue)
        val highSpeedSeek = view.findViewById<SeekBar>(R.id.highSpeedSeekBar)
        val switchCoolantAlert = view.findViewById<SwitchMaterial>(R.id.switchCoolantAlert)
        val coolantThresholdValue = view.findViewById<TextView>(R.id.coolantThresholdValue)
        val coolantThresholdSeek = view.findViewById<SeekBar>(R.id.coolantThresholdSeek)
        val switchNewFault = view.findViewById<SwitchMaterial>(R.id.switchNewFault)
        val switchConnLost = view.findViewById<SwitchMaterial>(R.id.switchConnLost)
        val switchConnRestored = view.findViewById<SwitchMaterial>(R.id.switchConnRestored)
        val switchEngineStart = view.findViewById<SwitchMaterial>(R.id.switchEngineStart)
        val switchEngineStop = view.findViewById<SwitchMaterial>(R.id.switchEngineStop)
        val switchEasterEggs = view.findViewById<SwitchMaterial>(R.id.switchEasterEggs)

        /** Guard against listener recursion on programmatic switch changes. */
        var updatingSwitches = false

        /** Binds a threshold SeekBar + label; slider writes instantly, getter feeds Save. */
        fun bindThreshold(
            seek: SeekBar,
            valueView: TextView,
            formatId: Int,
            min: Int,
            max: Int,
            step: Int,
            prefKey: String,
            def: Int
        ): () -> Int {
            val steps = (max - min) / step
            seek.max = steps
            var current = prefs.getInt(prefKey, def).coerceIn(min, max)
            fun render(v: Int) {
                valueView.text = getString(formatId, v)
            }
            seek.progress = ((current - min) / step).coerceIn(0, steps)
            render(current)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    current = min + progress * step
                    render(current)
                    prefs.edit { putInt(prefKey, current) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            return { current }
        }

        fun setThresholdEnabled(valueView: TextView, seek: SeekBar, enabled: Boolean) {
            valueView.isEnabled = enabled
            seek.isEnabled = enabled
            val alpha = if (enabled) 1f else 0.4f
            valueView.alpha = alpha
            seek.alpha = alpha
        }

        val getShiftRpm = bindThreshold(
            shiftSeek, shiftValue, R.string.settings_shift_rpm_value,
            PrefsKeys.SHIFT_RPM_MIN, PrefsKeys.SHIFT_RPM_MAX, PrefsKeys.SHIFT_RPM_STEP,
            PrefsKeys.SHIFT_RPM, PrefsKeys.DEFAULT_SHIFT_RPM
        )
        val getHighRpm = bindThreshold(
            highRpmSeek, highRpmValue, R.string.settings_shift_rpm_value,
            PrefsKeys.HIGH_RPM_MIN, PrefsKeys.HIGH_RPM_MAX, PrefsKeys.HIGH_RPM_STEP,
            PrefsKeys.HIGH_RPM_THRESHOLD, PrefsKeys.DEFAULT_HIGH_RPM
        )
        val getHighSpeed = bindThreshold(
            highSpeedSeek, highSpeedValue, R.string.settings_speed_value,
            PrefsKeys.HIGH_SPEED_MIN, PrefsKeys.HIGH_SPEED_MAX, PrefsKeys.HIGH_SPEED_STEP,
            PrefsKeys.HIGH_SPEED_THRESHOLD, PrefsKeys.DEFAULT_HIGH_SPEED
        )
        val getCoolantThreshold = bindThreshold(
            coolantThresholdSeek, coolantThresholdValue, R.string.settings_temp_value,
            PrefsKeys.OFFLINE_COOLANT_MIN, PrefsKeys.OFFLINE_COOLANT_MAX, PrefsKeys.OFFLINE_COOLANT_STEP,
            PrefsKeys.OFFLINE_COOLANT_THRESHOLD, PrefsKeys.DEFAULT_OFFLINE_COOLANT
        )
        val getOfflineVolume = bindThreshold(
            offlineVolumeSeek, offlineVolumeValue, R.string.settings_ai_volume_value,
            0, 100, 1, PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME
        )

        fun renderThresholdStates() {
            setThresholdEnabled(shiftValue, shiftSeek, switchShiftPoint.isChecked)
            setThresholdEnabled(highRpmValue, highRpmSeek, switchHighRpm.isChecked)
            setThresholdEnabled(highSpeedValue, highSpeedSeek, switchHighSpeed.isChecked)
            setThresholdEnabled(coolantThresholdValue, coolantThresholdSeek, switchCoolantAlert.isChecked)
        }

        fun renderOfflineVisibility() {
            offlineAiContent.visibility =
                if (switchOfflineAi.isChecked) View.VISIBLE else View.GONE
        }

        // Per-alert switches: instant save + threshold enable state.
        val alertSwitches = listOf(
            switchShiftPoint to PrefsKeys.SHIFT_POINT_ENABLED,
            switchHighRpm to PrefsKeys.HIGH_RPM_ENABLED,
            switchHighSpeed to PrefsKeys.HIGH_SPEED_ENABLED,
            switchCoolantAlert to PrefsKeys.OFFLINE_COOLANT_ENABLED,
            switchNewFault to PrefsKeys.NEW_FAULT_ENABLED,
            switchConnLost to PrefsKeys.CONN_LOST_ENABLED,
            switchConnRestored to PrefsKeys.CONN_RESTORED_ENABLED,
            switchEngineStart to PrefsKeys.ENGINE_START_ENABLED,
            switchEngineStop to PrefsKeys.ENGINE_STOP_ENABLED
        )
        val alertDefaults = mapOf(
            PrefsKeys.SHIFT_POINT_ENABLED to true,
            PrefsKeys.HIGH_RPM_ENABLED to true,
            PrefsKeys.HIGH_SPEED_ENABLED to true,
            PrefsKeys.OFFLINE_COOLANT_ENABLED to true,
            PrefsKeys.NEW_FAULT_ENABLED to true,
            PrefsKeys.CONN_LOST_ENABLED to true,
            PrefsKeys.CONN_RESTORED_ENABLED to true,
            PrefsKeys.ENGINE_START_ENABLED to true,
            PrefsKeys.ENGINE_STOP_ENABLED to false
        )
        for ((switch, key) in alertSwitches) {
            switch.isChecked = prefs.getBoolean(key, alertDefaults[key] == true)
            switch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean(key, isChecked) }
                renderThresholdStates()
            }
        }
        renderThresholdStates()

        fun applyOfflineOutputs(fromVoice: Boolean) {
            val (voiceOn, textOn) = LiveDataFragment.enforceOfflineOutput(
                fromVoice, switchOfflineVoice.isChecked, switchOfflineText.isChecked
            )
            prefs.edit {
                putBoolean(PrefsKeys.VOICE_INSIGHT, voiceOn)
                putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, textOn)
            }
            updatingSwitches = true
            switchOfflineVoice.isChecked = voiceOn
            switchOfflineText.isChecked = textOn
            updatingSwitches = false
        }
        switchOfflineVoice.isChecked = prefs.getBoolean(PrefsKeys.VOICE_INSIGHT, true)
        switchOfflineVoice.setOnCheckedChangeListener { _, _ ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            applyOfflineOutputs(fromVoice = true)
        }
        switchOfflineText.isChecked = prefs.getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)
        switchOfflineText.setOnCheckedChangeListener { _, _ ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            applyOfflineOutputs(fromVoice = false)
        }
        // Heal legacy both-off states so the at-least-one rule always holds.
        val (healedVoice, healedText) = LiveDataFragment.enforceOfflineOutput(
            true, switchOfflineVoice.isChecked, switchOfflineText.isChecked
        )
        if (healedVoice != switchOfflineVoice.isChecked || healedText != switchOfflineText.isChecked) {
            prefs.edit {
                putBoolean(PrefsKeys.VOICE_INSIGHT, healedVoice)
                putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, healedText)
            }
            updatingSwitches = true
            switchOfflineVoice.isChecked = healedVoice
            switchOfflineText.isChecked = healedText
            updatingSwitches = false
        }
        switchEasterEggs.isChecked = prefs.getBoolean(PrefsKeys.EASTER_EGGS_ENABLED, true)
        switchEasterEggs.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(PrefsKeys.EASTER_EGGS_ENABLED, isChecked) }
        }

        // -- Online AI (online assistant; Offline AI stays offline and untouched) --------
        val switchOnlineAi = view.findViewById<SwitchMaterial>(R.id.switchOnlineAi)
        val aiApiStatus = view.findViewById<TextView>(R.id.aiApiStatus)
        val freqSpinner = view.findViewById<Spinner>(R.id.freqSpinner)
        val personalitySpinner = view.findViewById<Spinner>(R.id.personalitySpinner)
        val aiVolumeValue = view.findViewById<TextView>(R.id.aiVolumeValue)
        val aiVolumeSeek = view.findViewById<SeekBar>(R.id.aiVolumeSeek)

        val frequencies = AiFrequency.entries
        freqSpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            frequencies.map { it.name.lowercase().replaceFirstChar(Char::titlecase) }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        freqSpinner.setSelection(
            frequencies.indexOf(AiFrequency.fromPref(prefs.getString(PrefsKeys.AI_FREQUENCY, null)))
                .coerceAtLeast(0)
        )

        val personalities = AiPersonality.entries
        personalitySpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            personalities.map { it.name.lowercase().replaceFirstChar(Char::titlecase) }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        personalitySpinner.setSelection(
            personalities.indexOf(AiPersonality.fromPref(prefs.getString(PrefsKeys.AI_PERSONALITY, null)))
                .coerceAtLeast(0)
        )

        var aiVolume = prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100)
        fun renderVolume(v: Int) {
            aiVolumeValue.text = getString(R.string.settings_ai_volume_value, v)
        }
        aiVolumeSeek.progress = aiVolume
        renderVolume(aiVolume)
        aiVolumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                aiVolume = progress
                renderVolume(aiVolume)
                prefs.edit { putInt(PrefsKeys.AI_VOLUME, aiVolume) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val switchOnlineVoice = view.findViewById<SwitchMaterial>(R.id.switchOnlineVoice)
        val switchOnlineText = view.findViewById<SwitchMaterial>(R.id.switchOnlineText)
        val onlineAiContent = view.findViewById<View>(R.id.onlineAiContent)

        fun renderOnlineVisibility() {
            onlineAiContent.visibility =
                if (switchOnlineAi.isChecked) View.VISIBLE else View.GONE
        }

        // Normalize once: the two assistants are mutually exclusive, so a
        // legacy state with both on settles to Offline ON / Online OFF.
        val offlineOn = prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true)
        var onlineOn = prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)
        if (offlineOn && onlineOn) {
            onlineOn = false
            prefs.edit { putBoolean(PrefsKeys.ONLINE_AI_ENABLED, false) }
        }
        switchOfflineAi.isChecked = offlineOn
        switchOnlineAi.isChecked = onlineOn
        switchOnlineVoice.isChecked = prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true)
        switchOnlineText.isChecked = prefs.getBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, true)
        renderOfflineVisibility()
        renderOnlineVisibility()
        renderThresholdStates()

        switchOfflineAi.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            val (newOffline, newOnline) = LiveDataFragment.resolveAiSwitches(
                LiveDataFragment.AiSystem.OFFLINE, isChecked, isChecked, switchOnlineAi.isChecked
            )
            prefs.edit {
                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, newOffline)
                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, newOnline)
            }
            updatingSwitches = true
            switchOfflineAi.isChecked = newOffline
            switchOnlineAi.isChecked = newOnline
            updatingSwitches = false
            renderOfflineVisibility()
            renderOnlineVisibility()
        }
        switchOnlineAi.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            val (newOffline, newOnline) = LiveDataFragment.resolveAiSwitches(
                LiveDataFragment.AiSystem.ONLINE, isChecked, switchOfflineAi.isChecked, isChecked
            )
            prefs.edit {
                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, newOffline)
                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, newOnline)
            }
            updatingSwitches = true
            switchOfflineAi.isChecked = newOffline
            switchOnlineAi.isChecked = newOnline
            updatingSwitches = false
            renderOfflineVisibility()
            renderOnlineVisibility()
        }

        fun applyOnlineOutputs(fromVoice: Boolean) {
            val (voiceOn, textOn) = LiveDataFragment.enforceOnlineOutput(
                fromVoice, switchOnlineVoice.isChecked, switchOnlineText.isChecked
            )
            prefs.edit {
                putBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, voiceOn)
                putBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, textOn)
            }
            updatingSwitches = true
            switchOnlineVoice.isChecked = voiceOn
            switchOnlineText.isChecked = textOn
            updatingSwitches = false
        }
        switchOnlineVoice.setOnCheckedChangeListener { _, _ ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            applyOnlineOutputs(fromVoice = true)
        }
        switchOnlineText.setOnCheckedChangeListener { _, _ ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            applyOnlineOutputs(fromVoice = false)
        }

        fun renderApiStatus() {
            val service = AiService(requireContext())
            val config = service.getConfig()
            aiApiStatus.text = if (service.hasApiKey()) {
                getString(
                    R.string.settings_ai_status_ok,
                    config.provider.displayName,
                    config.model.ifEmpty { config.provider.defaultModel }
                )
            } else {
                getString(R.string.settings_ai_status_missing)
            }
        }
        renderApiStatus()

        saveButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val selectedSpeedSource = if (radioGroupSpeedSource.checkedRadioButtonId == R.id.radioButtonGps) PrefsKeys.SPEED_SOURCE_DEVICE else PrefsKeys.SPEED_SOURCE_OBD
            val selectedProvider = providers[providerSpinner.selectedItemPosition]
            val apiKey = apiKeyEditText.text.toString().trim()
            prefs.edit {
                putString(PrefsKeys.AI_PROVIDER, selectedProvider.id)
                putString(PrefsKeys.OPENAI_API_KEY, apiKey)
                putString(PrefsKeys.OPENAI_MODEL_ID, modelIdEditText.text.toString().trim())
                putString(PrefsKeys.AI_BASE_URL, baseUrlEditText.text.toString().trim())
                putString(PrefsKeys.SPEED_SOURCE, selectedSpeedSource)
                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, switchOfflineAi.isChecked)
                putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, switchOfflineText.isChecked)
                putInt(PrefsKeys.OFFLINE_VOLUME, getOfflineVolume())
                putBoolean(PrefsKeys.SHIFT_POINT_ENABLED, switchShiftPoint.isChecked)
                putInt(PrefsKeys.SHIFT_RPM, getShiftRpm())
                putBoolean(PrefsKeys.HIGH_RPM_ENABLED, switchHighRpm.isChecked)
                putInt(PrefsKeys.HIGH_RPM_THRESHOLD, getHighRpm())
                putBoolean(PrefsKeys.HIGH_SPEED_ENABLED, switchHighSpeed.isChecked)
                putInt(PrefsKeys.HIGH_SPEED_THRESHOLD, getHighSpeed())
                putBoolean(PrefsKeys.OFFLINE_COOLANT_ENABLED, switchCoolantAlert.isChecked)
                putInt(PrefsKeys.OFFLINE_COOLANT_THRESHOLD, getCoolantThreshold())
                putBoolean(PrefsKeys.NEW_FAULT_ENABLED, switchNewFault.isChecked)
                putBoolean(PrefsKeys.CONN_LOST_ENABLED, switchConnLost.isChecked)
                putBoolean(PrefsKeys.CONN_RESTORED_ENABLED, switchConnRestored.isChecked)
                putBoolean(PrefsKeys.ENGINE_START_ENABLED, switchEngineStart.isChecked)
                putBoolean(PrefsKeys.ENGINE_STOP_ENABLED, switchEngineStop.isChecked)
                putBoolean(PrefsKeys.EASTER_EGGS_ENABLED, switchEasterEggs.isChecked)
                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, switchOnlineAi.isChecked)
                putBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, switchOnlineVoice.isChecked)
                putBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, switchOnlineText.isChecked)
                putString(PrefsKeys.AI_FREQUENCY, frequencies[freqSpinner.selectedItemPosition].prefValue)
                putString(PrefsKeys.AI_PERSONALITY, personalities[personalitySpinner.selectedItemPosition].prefValue)
                putInt(PrefsKeys.AI_VOLUME, aiVolumeSeek.progress)
            }
            renderApiStatus()
            if (selectedProvider.needsKey && apiKey.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.settings_api_key_missing, selectedProvider.displayName),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.buttonTestVoice).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            testOnlineAiVoice()
        }

        view.findViewById<Button>(R.id.buttonTtsSettings).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openSystemTtsSettings()
        }

        view.findViewById<Button>(R.id.buttonTestOfflineVoice).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            testOfflineAiVoice()
        }

        view.findViewById<Button>(R.id.buttonOfflineTtsSettings).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openSystemTtsSettings()
        }
    }

    /** Opens the Android system text-to-speech screen (engine, voices, rate). */
    private fun openSystemTtsSettings() {
        runCatching {
            // System text-to-speech screen (engine, language, voices).
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        }.onFailure { e ->
            Log.e("SettingsFragment", "Cannot open system TTS settings", e)
            Toast.makeText(
                context,
                getString(R.string.ai_test_failed, e.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var testTts: android.speech.tts.TextToSpeech? = null
    private var testOfflineTts: android.speech.tts.TextToSpeech? = null

    /** Speaks one sample line with the device voice (the only Online AI voice). */
    private fun testOnlineAiVoice() {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
            Toast.makeText(context, R.string.ai_test_off, Toast.LENGTH_SHORT).show()
            return
        }
        val line = getString(R.string.ai_test_line)
        stopTestVoice()
        val volume = prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100) / 100f
        val params = Bundle().apply {
            putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        testTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                runCatching { testTts?.language = java.util.Locale.getDefault() }
                testTts?.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test")
            } else if (isAdded) {
                Toast.makeText(
                    context,
                    getString(R.string.ai_test_failed, "TTS engine"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        Toast.makeText(context, R.string.ai_test_ok_device, Toast.LENGTH_SHORT).show()
    }

    private fun stopTestVoice() {
        runCatching { testTts?.stop() }
        runCatching { testOfflineTts?.stop() }
    }

    /** Speaks one sample line with the Offline AI device voice + volume. */
    private fun testOfflineAiVoice() {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
            Toast.makeText(context, R.string.ai_test_off, Toast.LENGTH_SHORT).show()
            return
        }
        val line = getString(R.string.offline_ai_test_line)
        stopTestVoice()
        val volume = prefs.getInt(PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME)
            .coerceIn(0, 100) / 100f
        val params = Bundle().apply {
            putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        testOfflineTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                runCatching { testOfflineTts?.language = java.util.Locale.getDefault() }
                testOfflineTts?.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test-offline")
            } else if (isAdded) {
                Toast.makeText(
                    context,
                    getString(R.string.ai_test_failed, "TTS engine"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        Toast.makeText(context, R.string.ai_test_ok_device, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTestVoice()
        runCatching { testTts?.shutdown() }
        testTts = null
        runCatching { testOfflineTts?.shutdown() }
        testOfflineTts = null
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
