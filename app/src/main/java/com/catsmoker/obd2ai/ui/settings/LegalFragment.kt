package com.catsmoker.obd2ai.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.catsmoker.obd2ai.R

/**
 * In-app legal reader. Shows the Privacy Policy or the Terms of Service from
 * the bundled string resources (no network, no WebView, no cookies) so the
 * policies are available offline and in every supported language. The
 * operator-hosted notices open in the browser via [openHostedLegal].
 */
class LegalFragment : Fragment() {
    private val args: LegalFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_legal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = view.findViewById<TextView>(R.id.legalTitle)
        val body = view.findViewById<TextView>(R.id.legalBody)
        if (args.page == PAGE_TERMS) {
            title.text = getString(R.string.legal_terms_title)
            body.text = getString(R.string.legal_terms_body)
        } else {
            title.text = getString(R.string.legal_privacy_title)
            body.text = getString(R.string.legal_privacy_body)
        }
        view.findViewById<Button>(R.id.legalOnlineButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (!openHostedLegal(requireContext())) {
                Toast.makeText(
                    context,
                    getString(R.string.no_errors_found, getString(R.string.legal_url)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val PAGE_PRIVACY = "privacy"
        const val PAGE_TERMS = "terms"

        /** Pure logic for tests: unknown values fall back to the privacy page. */
        fun isTermsPage(page: String): Boolean = page == PAGE_TERMS

        /**
         * Opens the operator-hosted legal page in the user's browser.
         * Nothing is sent by the app itself; the website's own policy
         * applies from there. Returns false when no app can handle it.
         */
        fun openHostedLegal(context: Context): Boolean = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.legal_url))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
    }
}
