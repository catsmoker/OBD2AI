package com.catsmoker.obd2ai.ui.common

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.catsmoker.obd2ai.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ErrorDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
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
