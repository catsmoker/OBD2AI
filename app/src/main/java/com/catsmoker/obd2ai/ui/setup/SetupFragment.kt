package com.catsmoker.obd2ai.ui.setup

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.AppLanguage
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.ThemeMode
import com.catsmoker.obd2ai.ui.settings.LegalFragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * First-launch setup in ONE layout ([R.layout.fragment_setup]) with two
 * step containers: 1 = agreement & privacy, 2 = language & appearance.
 * [showStep] toggles visibility — no new destinations, no second layout.
 *
 * No-flicker contract: language/theme previews write the same prefs
 * Settings uses and go through the same MainActivity apply methods, but
 * the manifest handles `uiMode|locale|layoutDirection` so they arrive as
 * [onConfigurationChanged] instead of destroying the Activity. Static
 * texts are rebound in place ([rebindTexts]); step, scroll position and
 * every selection survive. State has a single source of truth (prefs +
 * the saved step/agreement flag) — no parallel store.
 */
class SetupFragment : Fragment() {

    private var currentStep = STEP_1
    private var agreed = false

    /** Guards programmatic checks (init, locale rebind) from firing apply. */
    private var suppressChecks = false

    private var scrollView: ScrollView? = null
    private var stepLabel: TextView? = null
    private var stepTitle: TextView? = null
    private var progress: LinearProgressIndicator? = null
    private var step1: View? = null
    private var step2: View? = null
    private var nextButton: Button? = null
    private var languageGroup: RadioGroup? = null
    private var themeGroup: RadioGroup? = null
    private var analyticsSwitch: SwitchMaterial? = null
    private var personalizedSwitch: SwitchMaterial? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentStep = savedInstanceState?.getInt(KEY_STEP, STEP_1) ?: STEP_1
        agreed = savedInstanceState?.getBoolean(KEY_AGREED, false) == true
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val activity = activity as MainActivity

        scrollView = view.findViewById(R.id.setupScroll)
        stepLabel = view.findViewById(R.id.setupStepLabel)
        stepTitle = view.findViewById(R.id.setupStepTitle)
        progress = view.findViewById(R.id.setupProgress)
        step1 = view.findViewById(R.id.setupStep1)
        step2 = view.findViewById(R.id.setupStep2)
        nextButton = view.findViewById(R.id.setupNextButton)
        languageGroup = view.findViewById(R.id.setupLanguageGroup)
        themeGroup = view.findViewById(R.id.setupThemeGroup)
        analyticsSwitch = view.findViewById(R.id.setupAnalyticsSwitch)
        personalizedSwitch = view.findViewById(R.id.setupPersonalizedSwitch)

        // --- Step 1: agreement -------------------------------------------
        view.findViewById<Button>(R.id.setupTermsButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(
                SetupFragmentDirections.actionSetupFragmentToLegalFragment(
                    LegalFragment.PAGE_TERMS
                )
            )
        }
        view.findViewById<Button>(R.id.setupPrivacyButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(
                SetupFragmentDirections.actionSetupFragmentToLegalFragment(
                    LegalFragment.PAGE_PRIVACY
                )
            )
        }
        // No in-app cookies/WebView: the note says so and the operator site
        // (whose own policy applies there) opens in the user's browser.
        view.findViewById<Button>(R.id.setupOperatorButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (!LegalFragment.openHostedLegal(requireContext())) {
                Toast.makeText(
                    context,
                    getString(R.string.no_errors_found, getString(R.string.legal_url)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        view.findViewById<CheckBox>(R.id.setupAgreeCheck).apply {
            isChecked = agreed
            setOnCheckedChangeListener { _, isChecked ->
                agreed = isChecked
                renderNext()
            }
        }

        // --- Step 1: privacy switches (same prefs as Settings) ------------
        // Analytics wording stays honest: usage + device/app identifiers to
        // Firebase (pseudonymous, not anonymous); ads are always shown and
        // only personalization is optional (both default ON).
        val switchAnalytics = analyticsSwitch as SwitchMaterial
        val switchPersonalized = personalizedSwitch as SwitchMaterial
        switchAnalytics.isChecked = prefs.getBoolean(PrefsKeys.ANALYTICS_ENABLED, true)
        switchPersonalized.isChecked = prefs.getBoolean(PrefsKeys.PERSONALIZED_ADS, true)
        switchAnalytics.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(PrefsKeys.ANALYTICS_ENABLED, isChecked) }
        }
        switchPersonalized.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(PrefsKeys.PERSONALIZED_ADS, isChecked) }
        }

        view.findViewById<Button>(R.id.setupNextButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (canGoNext(agreed)) showStep(STEP_2, smoothScroll = true)
        }

        // --- Step 2: language (radio list, no spinner popup) ---------------
        bindLanguageRadios(view, prefs, activity)

        // --- Step 2: appearance -------------------------------------------
        bindThemeRadios(prefs, activity)

        view.findViewById<ImageButton>(R.id.setupStepBackArrow).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showStep(STEP_1, smoothScroll = true)
        }
        view.findViewById<Button>(R.id.setupBackButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showStep(STEP_1, smoothScroll = true)
        }
        view.findViewById<Button>(R.id.setupContinueButton).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            prefs.edit {
                putBoolean(PrefsKeys.TERMS_ACCEPTED, true)
                putBoolean(
                    PrefsKeys.ANALYTICS_ENABLED,
                    analyticsSwitch?.isChecked == true
                )
                putBoolean(
                    PrefsKeys.PERSONALIZED_ADS,
                    personalizedSwitch?.isChecked == true
                )
                putBoolean(PrefsKeys.CONSENT_SET, true)
            }
            // Language/theme were already applied live during the steps;
            // settle UMP (form if required) and apply everything, then home.
            activity.syncAdsConsent {
                if (!isAdded) return@syncAdsConsent
                if (!findNavController().popBackStack()) {
                    findNavController().navigate(R.id.onboardingFragment)
                }
            }
        }

        // Setup must finish before Home: system Back walks steps, never
        // drops onto the menu with consent unrecorded.
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentStep == STEP_2) {
                        showStep(STEP_1, smoothScroll = true)
                    } else {
                        Toast.makeText(
                            context,
                            getString(R.string.setup_require_note),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )

        rebindTexts()
        showStep(currentStep, smoothScroll = false)
    }

    private fun bindLanguageRadios(
        view: View,
        prefs: android.content.SharedPreferences,
        activity: MainActivity
    ) {
        val group = languageGroup ?: return
        val radios = listOf(
            view.findViewById<RadioButton>(R.id.setupLangSystem) to AppLanguage.SYSTEM,
            view.findViewById<RadioButton>(R.id.setupLangEnglish) to AppLanguage.ENGLISH,
            view.findViewById<RadioButton>(R.id.setupLangSpanish) to AppLanguage.SPANISH,
            view.findViewById<RadioButton>(R.id.setupLangArabic) to AppLanguage.ARABIC,
            view.findViewById<RadioButton>(R.id.setupLangChinese) to AppLanguage.CHINESE
        )
        for ((radio, lang) in radios) {
            radio.text = lang.displayName
        }
        suppressChecks = true
        group.check(
            radios.firstOrNull {
                it.second == AppLanguage.fromTag(prefs.getString(PrefsKeys.APP_LANGUAGE, null))
            }?.first?.id ?: R.id.setupLangSystem
        )
        suppressChecks = false
        group.setOnCheckedChangeListener { _, checkedId ->
            if (suppressChecks) return@setOnCheckedChangeListener
            val tag = radios.firstOrNull { it.first.id == checkedId }?.second?.tag
                ?: return@setOnCheckedChangeListener
            if (prefs.getString(PrefsKeys.APP_LANGUAGE, null) != tag) {
                prefs.edit { putString(PrefsKeys.APP_LANGUAGE, tag) }
                // No recreation (manifest configChanges): arrives as
                // onConfigurationChanged + rebindTexts, step/scroll kept.
                activity.applyAppLanguage(tag)
            }
        }
    }

    private fun bindThemeRadios(
        prefs: android.content.SharedPreferences,
        activity: MainActivity
    ) {
        val group = themeGroup ?: return
        suppressChecks = true
        group.check(
            when (activity.currentThemeMode(prefs)) {
                ThemeMode.LIGHT -> R.id.setupThemeLight
                ThemeMode.DARK -> R.id.setupThemeDark
                ThemeMode.SYSTEM -> R.id.setupThemeSystem
            }
        )
        suppressChecks = false
        group.setOnCheckedChangeListener { _, checkedId ->
            if (suppressChecks) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.setupThemeLight -> ThemeMode.LIGHT
                R.id.setupThemeDark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            if (activity.currentThemeMode(prefs) != mode) {
                prefs.edit { putString(PrefsKeys.THEME_MODE, mode.prefValue) }
                // No recreation (manifest configChanges): DayNight resources
                // update in place, setup step and selections untouched.
                activity.applyThemeMode(mode)
            }
        }
    }

    /**
     * Re-reads every static setup string from resources. Called after view
     * creation and on every configuration change (locale switch): the view
     * hierarchy is untouched, so step, scroll position and selections stay.
     */
    private fun rebindTexts() {
        val view = view ?: return
        suppressChecks = true
        try {
            view.findViewById<TextView>(R.id.setupTitle).text = getString(R.string.setup_title)
            view.findViewById<TextView>(R.id.setupAgreementDesc).text =
                getString(R.string.setup_agreement_desc)
            view.findViewById<Button>(R.id.setupTermsButton).text =
                getString(R.string.about_terms_button)
            view.findViewById<Button>(R.id.setupPrivacyButton).text =
                getString(R.string.about_privacy_button)
            view.findViewById<TextView>(R.id.setupCookieNote).text =
                getString(R.string.setup_cookies_note)
            view.findViewById<Button>(R.id.setupOperatorButton).text =
                getString(R.string.about_legal_online_button)
            view.findViewById<CheckBox>(R.id.setupAgreeCheck).apply {
                text = getString(R.string.setup_agree)
                if (isChecked != agreed) isChecked = agreed
            }
            view.findViewById<TextView>(R.id.setupPrivacyTitle).text =
                getString(R.string.settings_privacy_title)
            analyticsSwitch?.text = getString(R.string.settings_analytics_title)
            view.findViewById<TextView>(R.id.setupAnalyticsDesc).text =
                getString(R.string.settings_analytics_desc)
            personalizedSwitch?.text = getString(R.string.settings_ads_title)
            view.findViewById<TextView>(R.id.setupAdsDesc).text =
                getString(R.string.settings_ads_desc)
            view.findViewById<TextView>(R.id.setupRequireNote).text =
                getString(R.string.setup_require_note)
            view.findViewById<Button>(R.id.setupNextButton).text = getString(R.string.setup_next)
            view.findViewById<TextView>(R.id.setupLanguageTitle).text =
                getString(R.string.settings_language_title)
            view.findViewById<TextView>(R.id.setupLanguageDesc).text =
                getString(R.string.setup_language_desc)
            view.findViewById<TextView>(R.id.setupAppearanceTitle).text =
                getString(R.string.settings_theme_title)
            view.findViewById<TextView>(R.id.setupAppearanceDesc).text =
                getString(R.string.setup_appearance_desc)
            view.findViewById<RadioButton>(R.id.setupThemeSystem).text =
                getString(R.string.settings_theme_system)
            view.findViewById<RadioButton>(R.id.setupThemeLight).text =
                getString(R.string.settings_theme_light)
            view.findViewById<RadioButton>(R.id.setupThemeDark).text =
                getString(R.string.settings_theme_dark)
            view.findViewById<Button>(R.id.setupBackButton).text = getString(R.string.setup_back)
            view.findViewById<Button>(R.id.setupContinueButton).text =
                getString(R.string.setup_continue)
            renderStepHeader()
            renderNext()
        } finally {
            suppressChecks = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Locale/theme arrived without recreation: refresh strings in
        // place. Checked states, step and scroll position are untouched.
        rebindTexts()
    }

    private fun showStep(step: Int, smoothScroll: Boolean) {
        currentStep = step.coerceIn(STEP_1, STEP_2)
        val onFirst = currentStep == STEP_1
        step1?.visibility = if (onFirst) View.VISIBLE else View.GONE
        step2?.visibility = if (onFirst) View.GONE else View.VISIBLE
        renderStepHeader()
        renderNext()
        val scroller = scrollView ?: return
        if (smoothScroll) scroller.smoothScrollTo(0, 0) else scroller.scrollTo(0, 0)
        // Step change is announced via the live region on setupStepTitle
        // (see layout), so no manual announce call is needed here.
    }

    private fun renderStepHeader() {
        stepLabel?.text = getString(R.string.setup_step_indicator, currentStep, TOTAL_STEPS)
        stepTitle?.text = getString(
            if (currentStep == STEP_1) R.string.setup_agreement_title else R.string.setup_step2_title
        )
        progress?.progress = progressFor(currentStep)
    }

    private fun renderNext() {
        nextButton?.isEnabled = canGoNext(agreed)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, currentStep)
        outState.putBoolean(KEY_AGREED, agreed)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scrollView = null
        stepLabel = null
        stepTitle = null
        progress = null
        step1 = null
        step2 = null
        nextButton = null
        languageGroup = null
        themeGroup = null
        analyticsSwitch = null
        personalizedSwitch = null
    }

    companion object {
        const val STEP_1 = 1
        const val STEP_2 = 2
        const val TOTAL_STEPS = 2
        private const val KEY_STEP = "setup_step"
        private const val KEY_AGREED = "setup_agreed"

        /** Pure step state machine (tested): Next advances one step, clamped. */
        fun nextStep(current: Int): Int = (current + 1).coerceIn(STEP_1, STEP_2)

        /** Pure step state machine (tested): Back retreats one step, clamped. */
        fun prevStep(current: Int): Int = (current - 1).coerceIn(STEP_1, STEP_2)

        /** Step 1 gates on the Terms agreement; step 2 is ungated. */
        fun canGoNext(agreed: Boolean): Boolean = agreed

        /** Header progress for the step indicator (tested). */
        fun progressFor(step: Int): Int =
            if (step.coerceIn(STEP_1, STEP_2) == STEP_1) 50 else 100
    }
}
