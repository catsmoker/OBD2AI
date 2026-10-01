package com.catsmoker.obd2ai.ui.settings

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.BuildConfig
import com.catsmoker.obd2ai.R

/** App-level About destination (not a settings category). */
class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.settings_about_version, BuildConfig.VERSION_NAME)
        view.findViewById<Button>(R.id.aboutPrivacyButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(
                AboutFragmentDirections.actionAboutFragmentToLegalFragment(
                    LegalFragment.PAGE_PRIVACY
                )
            )
        }
        view.findViewById<Button>(R.id.aboutTermsButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(
                AboutFragmentDirections.actionAboutFragmentToLegalFragment(
                    LegalFragment.PAGE_TERMS
                )
            )
        }
        view.findViewById<Button>(R.id.aboutOnlineLegalButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (!LegalFragment.openHostedLegal(requireContext())) {
                Toast.makeText(
                    context,
                    getString(R.string.no_errors_found, getString(R.string.legal_url)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
