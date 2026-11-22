package com.catsmoker.obd2ai.ui.setup

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.core.content.edit
import androidx.navigation.NavHostController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.AppLanguage
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.ThemeMode
import com.catsmoker.obd2ai.ui.common.ConnectionStateStore
import com.catsmoker.obd2ai.ui.components.CircleIconButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.settings.LegalPages
import com.catsmoker.obd2ai.ui.theme.ObdSpacing

/**
 * First-launch setup, fully in Compose: one destination, three steps
 * rendered by [step] — 1 = agreement & privacy, 2 = language & appearance,
 * 3 = navigation style. State has a single source of truth (prefs + the
 * saved step/agreement flag); language/theme previews write the same prefs
 * Settings uses and go through the same MainActivity apply methods.
 *
 * No-flicker contract: the manifest handles `uiMode|locale|layoutDirection`
 * so previews arrive as configuration changes instead of destroying the
 * Activity — Compose re-reads resources on the next recomposition, and the
 * step/scroll/selection state is [rememberSaveable].
 */
@Composable
fun SetupDestination(activity: MainActivity, navController: NavHostController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = activity.getSharedPreferences(PrefsKeys.PREFS_NAME, 0)

    var step by rememberSaveable { mutableIntStateOf(SetupState.STEP_1) }
    var agreed by rememberSaveable { mutableStateOf(false) }

    var analytics by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PrefsKeys.ANALYTICS_ENABLED, true))
    }
    var personalized by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PrefsKeys.PERSONALIZED_ADS, true))
    }
    var languageTag by rememberSaveable {
        mutableStateOf(AppLanguage.fromTag(prefs.getString(PrefsKeys.APP_LANGUAGE, null)).tag)
    }
    var themeMode by rememberSaveable {
        mutableStateOf(activity.currentThemeMode(prefs).name)
    }

    fun tap() = haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)

    fun showStep(target: Int) {
        step = target.coerceIn(SetupState.STEP_1, SetupState.STEP_2)
    }

    // Setup must finish before Home: system Back walks steps, never drops
    // onto the menu with consent unrecorded.
    BackHandler {
        tap()
        if (step > SetupState.STEP_1) {
            showStep(SetupState.prevStep(step))
        } else {
            Toast.makeText(context, R.string.setup_require_note, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ObdSpacing.ScreenHorizontal, vertical = ObdSpacing.HeaderVertical)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(ObdSpacing.LabelGap))
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(ObdSpacing.CardGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.setup_step_indicator, step, SetupState.TOTAL_STEPS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(ObdSpacing.CardGap))
        LinearProgressIndicator(
            progress = { SetupState.progressFor(step) / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(ObdSpacing.CardGap))
        Text(
            text = stringResource(
                when (step) {
                    SetupState.STEP_1 -> R.string.setup_agreement_title
                    else -> R.string.setup_step2_title
                }
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(ObdSpacing.SectionGap))

        if (step == SetupState.STEP_1) {
            Text(
                text = stringResource(R.string.setup_agreement_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(ObdSpacing.CardGap))
            ObdOutlinedButton(
                onClick = {
                    tap()
                    navController.navigate("legal/${LegalPages.PAGE_TERMS}")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.about_terms_button)) }
            Spacer(Modifier.height(ObdSpacing.CardGap))
            ObdOutlinedButton(
                onClick = {
                    tap()
                    navController.navigate("legal/${LegalPages.PAGE_PRIVACY}")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.about_privacy_button)) }
            Spacer(Modifier.height(ObdSpacing.CardGap))
            Text(
                text = stringResource(R.string.setup_cookies_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(ObdSpacing.CardGap))
            ObdOutlinedButton(
                onClick = {
                    tap()
                    if (!LegalPages.openHostedLegal(context)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.no_errors_found, context.getString(R.string.legal_url)),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.about_legal_online_button)) }
            Spacer(Modifier.height(ObdSpacing.CardGap))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { agreed = !agreed },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Text(
                    text = stringResource(R.string.setup_agree),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(ObdSpacing.SectionGap))
            Text(
                text = stringResource(R.string.settings_privacy_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(ObdSpacing.CardGap))
            SwitchRow(
                checked = analytics,
                onCheckedChange = {
                    analytics = it
                    prefs.edit { putBoolean(PrefsKeys.ANALYTICS_ENABLED, it) }
                },
                title = stringResource(R.string.settings_analytics_title),
                desc = stringResource(R.string.settings_analytics_desc)
            )
            Spacer(Modifier.height(ObdSpacing.CardGap))
            SwitchRow(
                checked = personalized,
                onCheckedChange = {
                    personalized = it
                    prefs.edit { putBoolean(PrefsKeys.PERSONALIZED_ADS, it) }
                },
                title = stringResource(R.string.settings_ads_title),
                desc = stringResource(R.string.settings_ads_desc)
            )
            Spacer(Modifier.height(ObdSpacing.SectionGap))
            Text(
                text = stringResource(R.string.setup_require_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(ObdSpacing.CardGap))
            Button(
                onClick = {
                    tap()
                    showStep(SetupState.nextStep(step))
                },
                enabled = SetupState.canGoNext(agreed),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.setup_next)) }
        } else {
            CircleIconButton(
                icon = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.nav_back_desc),
                onClick = {
                    tap()
                    showStep(SetupState.prevStep(step))
                }
            )
            Spacer(Modifier.height(ObdSpacing.LabelGap))
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(ObdSpacing.LabelGap))
            Text(
                text = stringResource(R.string.setup_language_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(ObdSpacing.CardGap))
            SectionCard {
                listOf(
                    AppLanguage.SYSTEM, AppLanguage.ENGLISH, AppLanguage.SPANISH,
                    AppLanguage.ARABIC, AppLanguage.CHINESE
                ).forEach { lang ->
                    RadioRow(
                        selected = languageTag == lang.tag,
                        label = lang.displayName,
                        onClick = {
                            if (languageTag != lang.tag) {
                                languageTag = lang.tag
                                prefs.edit { putString(PrefsKeys.APP_LANGUAGE, lang.tag) }
                                // No recreation (manifest configChanges):
                                // Compose re-reads the new locale's strings.
                                activity.applyAppLanguage(lang.tag)
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(ObdSpacing.SectionGap))
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(ObdSpacing.LabelGap))
            Text(
                text = stringResource(R.string.setup_appearance_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(ObdSpacing.CardGap))
            SectionCard {
                listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                    RadioRow(
                        selected = themeMode == mode.name,
                        label = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                ThemeMode.DARK -> R.string.settings_theme_dark
                            }
                        ),
                        onClick = {
                            if (themeMode != mode.name) {
                                themeMode = mode.name
                                prefs.edit { putString(PrefsKeys.THEME_MODE, mode.prefValue) }
                                // No recreation (manifest configChanges):
                                // DayNight resources update in place.
                                activity.applyThemeMode(mode)
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(ObdSpacing.SectionGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ObdOutlinedButton(
                    onClick = {
                        tap()
                        showStep(SetupState.prevStep(step))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.setup_back)) }
                Button(
                    onClick = {
                        tap()
                        // Step 2 is the last step: it records consent and
                        // leaves setup for Home.
                        prefs.edit {
                            putBoolean(PrefsKeys.TERMS_ACCEPTED, true)
                            putBoolean(PrefsKeys.ANALYTICS_ENABLED, analytics)
                            putBoolean(PrefsKeys.PERSONALIZED_ADS, personalized)
                            putBoolean(PrefsKeys.CONSENT_SET, true)
                        }
                        // Language/theme were applied live during the steps;
                        // settle UMP (form if required) and apply everything,
                        // then home.
                        activity.syncAdsConsent {
                            ConnectionStateStore.refresh(activity.obdHelper)
                            // First run starts on setup with no Home entry
                            // beneath it, so popBackStack alone would strand
                            // the user here: replace setup with Home.
                            if (!navController.popBackStack("home", inclusive = false)) {
                                navController.navigate("home") {
                                    popUpTo("setup") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.setup_continue)) }
            }
        }
        Spacer(Modifier.height(ObdSpacing.SectionGap))
    }
}

/** Labeled switch row with an optional description line. */
@Composable
private fun SwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Selectable radio row used by the language/theme/navigation choices. */
@Composable
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Pure setup step state machine (tested). */
object SetupState {
    const val STEP_1 = 1
    const val STEP_2 = 2
    const val TOTAL_STEPS = 2

    /** Next advances one step, clamped. */
    fun nextStep(current: Int): Int = (current + 1).coerceIn(STEP_1, STEP_2)

    /** Back retreats one step, clamped. */
    fun prevStep(current: Int): Int = (current - 1).coerceIn(STEP_1, STEP_2)

    /** Step 1 gates on the Terms agreement; step 2 is ungated. */
    fun canGoNext(agreed: Boolean): Boolean = agreed

    /** Header progress for the step indicator. */
    fun progressFor(step: Int): Int = when (step.coerceIn(STEP_1, STEP_2)) {
        STEP_1 -> 50
        else -> 100
    }
}
