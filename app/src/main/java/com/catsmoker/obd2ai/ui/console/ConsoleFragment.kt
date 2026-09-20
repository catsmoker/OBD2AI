package com.catsmoker.obd2ai.ui.console

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.ObdHelper
import kotlinx.coroutines.launch

/**
 * Bounded transcript for the raw OBD console: newest-first thinking with a
 * fixed cap (200 lines) so a chatty adapter can never grow memory without
 * bound. Pure logic, tested.
 */
class ConsoleLog(private val capacity: Int = 200) {
    private val lines = ArrayDeque<String>()

    fun append(line: String) {
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }

    fun clear() {
        lines.clear()
    }

    fun snapshot(): List<String> = lines.toList()
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
