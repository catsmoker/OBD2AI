package com.catsmoker.obd2ai.ui.settings

import android.content.Context
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.net.Uri
import android.text.util.Linkify
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * In-app legal reader destination. Shows the Privacy Policy or the Terms of
 * Service from the bundled string resources (no network, no WebView, no
 * cookies) so the policies are available offline and in every supported
 * language. The operator-hosted notices open in the browser via
 * [LegalPages.openHostedLegal]. Ports the old fragment_legal.xml.
 */
@Composable
fun LegalDestination(page: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val isTerms = LegalPages.isTermsPage(page)

    ScreenScaffold(
        title = if (isTerms) stringResource(R.string.legal_terms_title)
        else stringResource(R.string.legal_privacy_title),
        onBack = onBack,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val bodyText = if (isTerms) stringResource(R.string.legal_terms_body)
            else stringResource(R.string.legal_privacy_body)
            // Linkified URLs + selectable text, like the XML autoLink/isSelectable.
            val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
            SectionCard {
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            autoLinkMask = Linkify.WEB_URLS
                            movementMethod = LinkMovementMethod.getInstance()
                            setTextIsSelectable(true)
                            setTextColor(onSurface)
                            textSize = 14f
                        }
                    },
                    update = { it.text = bodyText },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))

            ObdOutlinedButton(
                onClick = {
                    if (!LegalPages.openHostedLegal(context)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.no_errors_found, context.getString(R.string.legal_url)),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.about_legal_online_button))
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))
        }
    }
}

/**
 * Legal page constants and pure logic, moved out of the deleted fragment so
 * the unit tests and the other screens keep one home for them.
 */
object LegalPages {
    const val PAGE_PRIVACY = "privacy"
    const val PAGE_TERMS = "terms"

    /** Pure logic for tests: unknown values fall back to the privacy page. */
    fun isTermsPage(page: String): Boolean = page == PAGE_TERMS

    /**
     * Opens the operator-hosted legal page in the user's browser. Nothing is
     * sent by the app itself; the website's own policy applies from there.
     * Returns false when no app can handle it.
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
