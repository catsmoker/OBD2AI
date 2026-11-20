package com.catsmoker.obd2ai

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.catsmoker.obd2ai.ads.AdBillingManager
import kotlinx.coroutines.launch
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
import com.catsmoker.obd2ai.ui.AppRoot
import com.catsmoker.obd2ai.ui.common.ConnectionStateStore

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var bluetoothHelper: BluetoothHelper
    @Inject
    lateinit var obdHelper: ObdHelper
    @Inject
    lateinit var aiService: AiService
    @Inject
    lateinit var adBilling: AdBillingManager
    /** Created on first use (off the launch path): Firebase init does disk I/O. */
    val firebaseAnalytics: FirebaseAnalytics by lazy { Firebase.analytics }

    /** The banner instance, hosted by the Compose tree (see AppRoot's
     * AdBanner). Null until composition mounts it; every ad call is
     * null-safe so entitlement flips before first composition are no-ops. */
    var adView: AdView? = null
        private set

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

    /** Creates and wires the banner view; called from the Compose tree. */
    fun createAdView(): AdView = AdView(this).also { view ->
        adView = view
        view.adListener = object : AdListener() {
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
    }

    fun applyAdChoice() {
        val adView = adView ?: return
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
        if (!adsRemovedApplied) adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!adsRemovedApplied) adView?.resume()
    }

    override fun onDestroy() {
        if (!adsRemovedApplied) adView?.destroy()
        adView = null
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
        // Helpers are field-injected by Hilt before onCreate. The transport
        // loss hook must be re-armed here because injection creates a fresh
        // ObdHelper per activity instance.
        obdHelper.onConnectionLost = {
            runOnUiThread { ConnectionStateStore.refresh(obdHelper) }
        }
        adBilling.start()
        // Entitlement flips (restore on start, purchase any time) re-run
        // the single ad gate: grant tears the banner down immediately.
        lifecycleScope.launch { adBilling.isAdsRemoved.collect { applyAdChoice() } }
        enableEdgeToEdge()
        // Single-activity Compose root: navigation, chrome and the ad banner
        // all live in the composition — no fragments, no XML layouts.
        setContent {
            AppRoot(this, startOnSetup = !prefs.contains(PrefsKeys.CONSENT_SET))
        }

        // Banner ads are always on (they fund the free app); analytics
        // collection stays off (manifest default + choice rules) until the
        // setup screen records the choice. The UMP flow runs first so a
        // required consent form (EEA/UK) shows before any ad request.
        syncAdsConsent {}
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
