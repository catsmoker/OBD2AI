package com.catsmoker.obd2ai.ui.diagnostics

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.common.ErrorDialogFragment
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.launch

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
        private val senderView: TextView = itemView.findViewById(R.id.textViewChatSender)
        private val textView: TextView = itemView.findViewById(R.id.textViewChatMessage)

        fun bind(message: ChatMessage) {
            textView.text = message.text
            // Driver bubbles right in primary-container + bold, AI answers left
            // on a neutral surface with a sender label — one system, two voices.
            val side = if (message.fromUser) Gravity.END else Gravity.START
            val senderParams = senderView.layoutParams as android.widget.LinearLayout.LayoutParams
            senderParams.gravity = side
            senderView.layoutParams = senderParams
            senderView.text = itemView.context.getString(
                if (message.fromUser) R.string.chat_label_you else R.string.chat_label_ai
            )
            val bubbleParams = textView.layoutParams as android.widget.LinearLayout.LayoutParams
            bubbleParams.gravity = side
            textView.layoutParams = bubbleParams
            textView.setBackgroundResource(
                if (message.fromUser) R.drawable.bg_badge else R.drawable.bg_bubble_ai
            )
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
