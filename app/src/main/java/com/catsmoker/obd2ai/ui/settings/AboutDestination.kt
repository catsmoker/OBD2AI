package com.catsmoker.obd2ai.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.catsmoker.obd2ai.BuildConfig
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * App-level About destination (not a settings category): version, contact
 * and the legal entry points. Ports the old fragment_about.xml.
 */
@Composable
fun AboutDestination(navController: NavHostController) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ObdSpacing.ScreenHorizontal, vertical = ObdSpacing.HeaderVertical)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ObdSpacing.CardGap)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_gauge),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(ObdSpacing.SectionGap))
        SectionCard {
            Text(
                text = stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(ObdSpacing.LabelGap))
            Text(
                text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(ObdSpacing.SectionGap))
            Text(
                text = stringResource(R.string.about_contact_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(ObdSpacing.LabelGap))
            Text(
                text = stringResource(R.string.about_contact_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(ObdSpacing.SectionGap))
            ObdOutlinedButton(
                onClick = { navController.navigate("legal/${LegalPages.PAGE_PRIVACY}") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.about_privacy_button))
            }
            Spacer(Modifier.height(ObdSpacing.CardGap))
            ObdOutlinedButton(
                onClick = { navController.navigate("legal/${LegalPages.PAGE_TERMS}") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.about_terms_button))
            }
            Spacer(Modifier.height(ObdSpacing.CardGap))
            ObdOutlinedButton(
                onClick = {
                    if (!LegalPages.openHostedLegal(context)) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.no_errors_found, context.getString(R.string.legal_url)),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.about_legal_online_button))
            }
        }
    }
}
