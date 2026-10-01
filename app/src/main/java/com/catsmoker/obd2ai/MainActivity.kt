package com.catsmoker.obd2ai

import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavOptions
import com.catsmoker.obd2ai.ui.common.AppNav
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.catsmoker.obd2ai.ads.AdsConsent
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase // keep: Firebase.analytics backing the lazy delegate
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.obd.BluetoothHelper
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.prefs.AppLanguage
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.ThemeMode
import com.catsmoker.obd2ai.ui.common.applySystemBarInsets

class MainActivity : AppCompatActivity() {

    lateinit var bluetoothHelper: BluetoothHelper
    lateinit var obdHelper: ObdHelper
    lateinit var aiService: AiService
    /** Created on first use (off the launch path): Firebase init does disk I/O. */
    val firebaseAnalytics: FirebaseAnalytics by lazy { Firebase.analytics }

    /** Stored analytics choice (default ON). Effective only after the
     * first-launch choice exists — see [analyticsEffective]. */
    fun isAnalyticsEnabled(): Boolean =
        getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PrefsKeys.ANALYTICS_ENABLED, true)

    /** Stored ad-personalization choice (default ON). The banner itself is
     * always on — it keeps the app free and cannot be disabled. Effective
     * only after the first-launch choice exists — see [AdsConsent]. */
    fun isPersonalizedAds(): Boolean =
        getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PrefsKeys.PERSONALIZED_ADS, true)

    /** True once the first-launch choice dialog has completed. */
    private fun hasPrivacyChoice(): Boolean =
        getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
            .contains(PrefsKeys.CONSENT_SET)

    /** Central gate: no analytics event leaves the device before/without opt-in. */
    fun logAnalyticsEvent(name: String) {
        if (!AdsConsent.analyticsEffective(hasPrivacyChoice(), isAnalyticsEnabled())) return
        runCatching { firebaseAnalytics.logEvent(name, null) }
    }

    /** Applies the stored privacy choices to Firebase + AdMob. Safe to re-run. */
    fun applyPrivacyChoices() {
        if (AdsConsent.analyticsEffective(hasPrivacyChoice(), isAnalyticsEnabled())) {
            runCatching { firebaseAnalytics.setAnalyticsCollectionEnabled(true) }
        } else {
            // Collection is off by default (manifest) and before the first
            // choice; re-assert off on opt-out. Touching the lazy delegate
            // here is intentional: consent state changed.
            runCatching { firebaseAnalytics.setAnalyticsCollectionEnabled(false) }
        }
        applyAdChoice()
    }

    private var adsInitialized = false

    /** Live UMP state: true while regulator-required consent is pending. */
    private fun umpConsentRequired(): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(this).consentStatus ==
            ConsentInformation.ConsentStatus.REQUIRED
    }.getOrDefault(false)

    /** Effective personalization for this ad load (see [AdsConsent]). */
    private fun effectivePersonalizedAds(): Boolean =
        AdsConsent.effectivePersonalizedAds(
            hasPrivacyChoice(),
            isPersonalizedAds(),
            umpConsentRequired()
        )

    /**
     * Runs the Google consent flow, then settles with [applyPrivacyChoices].
     * Never writes preferences itself: the first-launch dialog and the
     * Settings toggles are the sole writers, so a stored OFF can never be
     * resurrected by a later UMP status. Callers get exactly one callback
     * with the resulting UMP status. Never throws.
     */
    fun syncAdsConsent(onDone: (Int) -> Unit = {}) {
        val settled = { status: Int ->
            applyPrivacyChoices()
            onDone(status)
        }
        runCatching {
            val info = UserMessagingPlatform.getConsentInformation(this)
            info.requestConsentInfoUpdate(
                this,
                ConsentRequestParameters.Builder().build(),
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                        settled(info.consentStatus)
                    }
                },
                { settled(info.consentStatus) }
            )
        }.onFailure {
            Log.w("MainActivity", "UMP consent update failed; using stored choices", it)
            settled(ConsentInformation.ConsentStatus.UNKNOWN)
        }
    }

    private fun buildAdRequest(): AdRequest {
        val builder = AdRequest.Builder()
        if (!effectivePersonalizedAds()) {
            // Non-personalized ads: generic creatives, no interest profile.
            builder.addNetworkExtrasBundle(
                AdMobAdapter::class.java,
                Bundle().apply { putString("npa", "1") }
            )
        }
        return builder.build()
    }

    private fun applyAdChoice() {
        val adView = findViewById<AdView>(R.id.adView) ?: return
        // Always on: banner ads fund the free app and cannot be disabled.
        // Only personalization is user-controlled — except where the UMP
        // flow says ads must wait for consent (EEA/UK with configured
        // Funding Choices messages and no consent yet).
        val canRequest = runCatching {
            UserMessagingPlatform.getConsentInformation(this).canRequestAds()
        }.getOrDefault(true)
        if (!canRequest) {
            adView.visibility = View.GONE
            return
        }
        adView.visibility = View.VISIBLE
        if (!adsInitialized) {
            adsInitialized = true
            MobileAds.initialize(this) {
                val testDeviceIds = listOf("AB065C801A1B4DA9FCCDBC44E5483FDD")
                val configuration =
                    RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
                MobileAds.setRequestConfiguration(configuration)
            }
        }
        // Wide, short adaptive banner sized to the actual container width
        // (tablets get a full-width banner, phones the classic size).
        // Loaded one frame after layout so width is measured, never faked.
        adView.post {
            runCatching {
                val widthPx = if (adView.width > 0) adView.width
                else resources.displayMetrics.widthPixels
                val adWidthDp = (widthPx / resources.displayMetrics.density)
                    .toInt().coerceAtLeast(320)
                adView.setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidthDp)
                )
            }
            adView.loadAd(buildAdRequest())
        }
    }

    fun currentThemeMode(prefs: android.content.SharedPreferences): ThemeMode {
        if (prefs.contains(PrefsKeys.THEME_MODE)) {
            return ThemeMode.fromPref(prefs.getString(PrefsKeys.THEME_MODE, null))
        }
        // One-time migration from the legacy dark-mode switch.
        val legacy = ThemeMode.fromLegacyDarkMode(prefs.getBoolean(PrefsKeys.DARK_MODE, false))
        prefs.edit { putString(PrefsKeys.THEME_MODE, legacy.prefValue) }
        return legacy
    }

    fun applyThemeMode(mode: ThemeMode) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun applyAppLanguage(tag: String) {
        val locales = if (AppLanguage.fromTag(tag) == AppLanguage.SYSTEM) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.create(java.util.Locale.forLanguageTag(tag))
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * The manifest declares `uiMode|locale|layoutDirection` handling so the
     * setup screen's live language/theme previews arrive here instead of
     * destroying the Activity (no navigation reset, no lost setup step).
     * AppCompat applies the new resources; each visible fragment rebinds
     * its own static texts (see SetupFragment). Rotation still recreates
     * (land/tablet variants rely on it).
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        applyThemeMode(currentThemeMode(prefs))
        applyAppLanguage(prefs.getString(PrefsKeys.APP_LANGUAGE, null).orEmpty())
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Helpers before setContentView: the start destination (home) reads
        // them while its view is created during layout inflation.
        bluetoothHelper = BluetoothHelper(this)
        obdHelper = ObdHelper(bluetoothHelper)
        aiService = AiService(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.activityRoot).applySystemBarInsets()

        // Banner ads are always on (they fund the free app); analytics
        // collection stays off (manifest default + choice rules) until the
        // setup screen records the choice. The UMP flow runs first so a
        // required consent form (EEA/UK) shows before any ad request.
        syncAdsConsent {}

        val adView = findViewById<AdView>(R.id.adView)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdListener", "Ad loaded.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("AdListener", "Ad failed to load: ${adError.message}, code: ${adError.code}")
            }

            override fun onAdOpened() {
                Log.d("AdListener", "Ad opened.")
            }

            override fun onAdClicked() {
                Log.d("AdListener", "Ad clicked.")
            }

            override fun onAdClosed() {
                Log.d("AdListener", "Ad closed.")
            }
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        // Tablets (sw600dp layout) navigate with the rail instead.
        val rail =
            findViewById<com.google.android.material.navigationrail.NavigationRailView>(
                R.id.navigation_rail
            )
        // Phones navigate with the compact in-flow top bar instead of the
        // old overlaid shortcut (which could cover fragment controls).
        val topBar: View? = findViewById(R.id.top_nav_bar)
        // Visible back arrow (phone): shown on every chromed screen except
        // the home root. Same action as system Back (navigateUp below).
        val topNavBack: ImageButton? = findViewById(R.id.nav_back)
        // Visible back arrow (tablet): rail header, toggled per destination.
        val railHeaderBack: View? = findViewById(R.id.rail_back)
        val topNavButtons: List<ImageButton> = listOfNotNull(
            findViewById(R.id.nav_dashboard),
            findViewById(R.id.nav_diagnostics),
            findViewById(R.id.nav_trip),
            findViewById(R.id.nav_console),
            findViewById(R.id.nav_settings),
            findViewById(R.id.nav_about)
        )
        val topNavDestinations = listOf(
            R.id.liveDataFragment,
            R.id.errorOverviewFragment,
            R.id.tripFragment,
            R.id.consoleFragment,
            R.id.settingsFragment,
            R.id.aboutFragment
        )

        // Section mapping + first-run set live in AppNav (tested), so the
        // phone bar and the tablet rail can never disagree about highlight
        // or back-arrow state.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val section = AppNav.sectionFor(destination.id)
            if (rail != null) {
                // Rail layout: rail owns top-level navigation, and the rail
                // hides itself on the transient setup screens.
                rail.visibility =
                    if (AppNav.isChromeHidden(destination.id)) View.GONE else View.VISIBLE
                if (section != null) {
                    rail.menu.findItem(section)?.isChecked = true
                } else {
                    for (i in 0 until rail.menu.size()) {
                        rail.menu.getItem(i).isChecked = false
                    }
                }
                railHeaderBack?.visibility =
                    if (AppNav.showBackArrow(destination.id)) View.VISIBLE else View.GONE
            } else {
                // Phone layout: the in-flow top bar replaces the old overlaid
                // shortcut (which could cover fragment controls). It hides
                // on the transient setup screens; the selected icon tracks
                // the destination.
                topBar?.visibility =
                    if (AppNav.isChromeHidden(destination.id)) View.GONE else View.VISIBLE
                topNavBack?.visibility =
                    if (AppNav.showBackArrow(destination.id)) View.VISIBLE else View.GONE
                val selected = section
                val selectedColor = getColor(R.color.colorSecondary)
                val idleColor = getColor(R.color.on_surface)
                // Leaf screens (settings/about/legal) keep the bar for the
                // back arrow but hide the section buttons: back-arrow-only.
                val sectionsVisible = AppNav.showSectionButtons(destination.id)
                topNavButtons.forEachIndexed { index, button ->
                    button.visibility = if (sectionsVisible) View.VISIBLE else View.GONE
                    val active = topNavDestinations[index] == selected
                    button.imageTintList =
                        android.content.res.ColorStateList.valueOf(
                            if (active) selectedColor else idleColor
                        )
                }
            }
        }

        // Top-level jumps collapse everything above the welcome root and
        // reuse the destination when already there: Back always returns
        // toward the welcome menu, never through discarded sections.
        fun goSection(destId: Int) {
            if (navController.currentDestination?.id == destId) return
            navController.navigate(
                destId,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.onboardingFragment, false)
                    .build()
            )
        }

        topNavBack?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            navController.navigateUp()
        }

        topNavButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                goSection(topNavDestinations[index])
            }
        }

        val railView = rail
        railView?.setOnItemSelectedListener { item ->
            railView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            goSection(item.itemId)
            true
        }
        railHeaderBack?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            navController.navigateUp()
        }

        // First launch shows the setup screen once, on top of the menu;
        // every later launch starts directly on the menu.
        if (!prefs.contains(PrefsKeys.CONSENT_SET)) {
            navController.navigate(R.id.setupFragment)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        bluetoothHelper.resolvePermissionsResult(requestCode, grantResults)
    }
}
