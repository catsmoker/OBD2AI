package com.catsmoker.obd2ai

import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.catsmoker.obd2ai.ads.AdBillingManager
import kotlinx.coroutines.launch
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavOptions
import com.catsmoker.obd2ai.ui.common.AppNav
import com.catsmoker.obd2ai.ui.common.ConnectionState
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
import com.catsmoker.obd2ai.prefs.NavStyle
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.ThemeMode
import com.catsmoker.obd2ai.ui.common.applySystemBarInsets

class MainActivity : AppCompatActivity() {

    lateinit var bluetoothHelper: BluetoothHelper
    lateinit var obdHelper: ObdHelper
    lateinit var aiService: AiService
    /** Paid ad removal (Play Billing `remove_ads`); fragments read the
     * entitlement through this, never through preferences directly. */
    lateinit var adBilling: AdBillingManager
    /** Created on first use (off the launch path): Firebase init does disk I/O. */
    val firebaseAnalytics: FirebaseAnalytics by lazy { Firebase.analytics }

    // Navigation chrome. Null where the active layout lacks it: the bottom
    // dock exists only on phones, the rail + back arrow only on tablets.
    private var navController: androidx.navigation.NavController? = null
    private var rail: com.google.android.material.navigationrail.NavigationRailView? = null
    private var railHeaderBack: View? = null
    private var bottomNav: com.google.android.material.bottomnavigation.BottomNavigationView? = null

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

    /**
     * True when Google requires a revocable consent entry point (regions
     * with Funding Choices messages, e.g. EEA/UK). The Settings Privacy
     * card shows the "Ad consent options" button only then. Never throws.
     */
    fun canShowAdPrivacyOptions(): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(this).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }.getOrDefault(false)

    /**
     * Revocation link: re-opens the Google privacy-options form so the
     * user can review or withdraw ad consent after the first-launch
     * choice. Settles with [applyPrivacyChoices] and reports the
     * resulting UMP status, mirroring [syncAdsConsent]. Never writes
     * preferences itself — the Settings toggles stay the sole writers.
     * Never throws.
     */
    fun showAdPrivacyOptions(onDone: (Int) -> Unit = {}) {
        val settled = {
            applyPrivacyChoices()
            val status = runCatching {
                UserMessagingPlatform.getConsentInformation(this).consentStatus
            }.getOrDefault(ConsentInformation.ConsentStatus.UNKNOWN)
            onDone(status)
        }
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(this) {
                settled()
            }
        }.onFailure {
            Log.w("MainActivity", "UMP privacy options form failed; using stored choices", it)
            settled()
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

    /** True once a granted entitlement has torn the banner down. Grants
     * only ever land here mid-session; revocations surface on the next
     * start (fresh Activity, fresh AdView), so this never flips back. */
    private var adsRemovedApplied = false

    private fun applyAdChoice() {
        val adView = findViewById<AdView>(R.id.adView) ?: return
        if (adBilling.isAdsRemoved.value) {
            // Paid entitlement: never load an ad, and tear down any
            // creative already on screen instead of merely hiding it.
            adsRemovedApplied = true
            adView.visibility = View.GONE
            runCatching { adView.destroy() }
            return
        }
        // Always on unless purchased: banner ads fund the free app and
        // cannot be disabled for free.
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
                // Debug only: this device gets Google test ads so manual
                // testing never generates invalid production impressions.
                // Release builds always request live ads.
                if (BuildConfig.DEBUG) {
                    val testDeviceIds = listOf("AB065C801A1B4DA9FCCDBC44E5483FDD")
                    val configuration =
                        RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
                    MobileAds.setRequestConfiguration(configuration)
                }
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
                // Large anchored adaptive banner: the SDK v25 replacement
                // for the deprecated current-orientation sizing.
                adView.setAdSize(
                    AdSize.getLargeAnchoredAdaptiveBannerAdSize(this, adWidthDp)
                )
            }
            adView.loadAd(buildAdRequest())
        }
    }

    override fun onPause() {
        if (!adsRemovedApplied) findViewById<AdView>(R.id.adView)?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!adsRemovedApplied) findViewById<AdView>(R.id.adView)?.resume()
    }

    override fun onDestroy() {
        if (!adsRemovedApplied) findViewById<AdView>(R.id.adView)?.destroy()
        super.onDestroy()
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
        adBilling = AdBillingManager(this)
        adBilling.start()
        // Entitlement flips (restore on start, purchase any time) re-run
        // the single ad gate: grant tears the banner down immediately.
        lifecycleScope.launch { adBilling.isAdsRemoved.collect { applyAdChoice() } }
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
        navController = navHostFragment.navController
        // Tablets (sw600dp layout) navigate with the rail instead.
        rail =
            findViewById<com.google.android.material.navigationrail.NavigationRailView>(
                R.id.navigation_rail
            )
        // Tablet rail back arrow in the header.
        railHeaderBack = findViewById(R.id.rail_back)
        // Phones navigate with a bottom navigation dock (Home / Live /
        // Diagnostics / Trip). Secondary screens (Console, Settings, About)
        // live on the Home screen; nothing highlights in the dock for them.
        // Null on the tablet layout (sw600dp), which uses the rail instead —
        // every dock reference must be null-safe or tablets crash at launch.
        bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView?>(
            R.id.bottom_nav
        )

        navController?.addOnDestinationChangedListener { _, _, _ ->
            // Visibility depends on both the destination and the live
            // connection state — updateNavChrome reads both.
            updateNavChrome()
        }

        /** Collapse the nav stack to a known destination; reuse when already there. */
        fun goSection(destId: Int) {
            val nav = navController ?: return
            if (nav.currentDestination?.id == destId) return
            nav.navigate(
                destId,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.onboardingFragment, false)
                    .build()
            )
        }

        // Single dispatch for dock taps. Re-taps on the active tab are the
        // standard no-op — every destination has its own tab or Home tile.
        bottomNav?.setOnItemSelectedListener { item ->
            bottomNav?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            goSection(item.itemId)
            true
        }

        val railView = rail
        railView?.setOnItemSelectedListener { item ->
            railView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            goSection(item.itemId)
            true
        }
        railHeaderBack?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            navController?.navigateUp()
        }

        // First launch shows the setup screen once, on top of the menu;
        // every later launch starts directly on the menu.
        if (!prefs.contains(PrefsKeys.CONSENT_SET)) {
            navController?.navigate(R.id.setupFragment)
        }
    }

    /** Check the menu item for [section], or clear all when null. */
    private fun syncHighlight(menu: android.view.Menu, section: Int?) {
        if (section != null) {
            menu.findItem(section)?.isChecked = true
        } else {
            for (i in 0 until menu.size()) {
                menu.getItem(i).isChecked = false
            }
        }
    }

    /**
     * Recompute chrome (dock/rail) visibility and highlight for the current
     * destination. Chrome appears only once the app has a data world to
     * navigate — the user picked Connect or Try Demo — and hides again on
     * disconnect: every tab leads to a screen that needs a connection.
     * Called on every destination change and from the Home menu's
     * refreshState(), so starting demo makes the bar appear in place,
     * with no navigation.
     */
    fun updateNavChrome() {
        val nav = navController ?: return
        val dest = nav.currentDestination ?: return
        val linked = ConnectionState.resolve(
            obdHelper.demoMode,
            obdHelper.isConnected
        ) != ConnectionState.DISCONNECTED
        // Cards mode (chosen at first start) means hub-and-spoke: no bar.
        val cardsMode = NavStyle.fromPref(
            getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
                .getString(PrefsKeys.NAV_STYLE, null)
        ) == NavStyle.CARDS
        val hidden = AppNav.isChromeHidden(dest.id, linked, cardsMode)
        val section = AppNav.sectionFor(dest.id)
        val railView = rail
        if (railView != null) {
            // Tablet: the rail owns top-level navigation.
            railView.visibility = if (hidden) View.GONE else View.VISIBLE
            syncHighlight(railView.menu, section)
            railHeaderBack?.visibility =
                if (AppNav.showBackArrow(dest.id)) View.VISIBLE else View.GONE
        } else {
            // Phone: the bottom dock.
            bottomNav?.visibility = if (hidden) View.GONE else View.VISIBLE
            bottomNav?.let { syncHighlight(it.menu, section) }
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
