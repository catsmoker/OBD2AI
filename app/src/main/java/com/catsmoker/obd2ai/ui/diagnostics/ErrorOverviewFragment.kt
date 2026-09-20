package com.catsmoker.obd2ai.ui.diagnostics

import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.diagnostics.DtcDictionary
import com.catsmoker.obd2ai.diagnostics.DtcInfo
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import com.catsmoker.obd2ai.diagnostics.MilStatus
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.common.ErrorDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import android.content.Context

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
        private val code: TextView = view.findViewById(R.id.codeBadge)
        private val title: TextView = view.findViewById(R.id.title)
        private val detail: TextView = view.findViewById(R.id.detail)
        private val pillContainer: View = view.findViewById(R.id.pill_container)
        private val pillStored: TextView = view.findViewById(R.id.pill_stored)
        private val pillPending: TextView = view.findViewById(R.id.pill_pending)
        private val pillPermanent: TextView = view.findViewById(R.id.pill_permanent)

        fun bind(item: DtpCodeDTO, onItemClick: (DtpCodeDTO) -> Unit) {
            code.text = item.errorCode
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

        view.findViewById<Button>(R.id.button_clear_codes).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            firebaseAnalytics.logEvent("clear_codes_clicked", null)
            confirmAndClearCodes()
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
        MaterialAlertDialogBuilder(requireContext())
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
