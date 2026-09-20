package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.common.ErrorDialogFragment
import kotlinx.coroutines.launch

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
