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
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, MODE_PRIVATE)
        applyThemeMode(currentThemeMode(prefs))
        applyAppLanguage(prefs.getString(PrefsKeys.APP_LANGUAGE, null).orEmpty())
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.activityRoot).applySystemBarInsets()

        MobileAds.initialize(this) {
            val testDeviceIds = listOf("AB065C801A1B4DA9FCCDBC44E5483FDD")
            val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
            MobileAds.setRequestConfiguration(configuration)
        }

        // Wide, short adaptive banner sized to the actual container width
        // (tablets get a full-width banner, phones the classic size).
        // Loaded one frame after layout so width is measured, never faked.
        val adView = findViewById<AdView>(R.id.adView)
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
            adView.loadAd(AdRequest.Builder().build())
        }

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

        bluetoothHelper = BluetoothHelper(this)
        obdHelper = ObdHelper(bluetoothHelper)
        aiService = AiService(this)

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

        // Top-level destinations reachable from the rail; detail screens
        // resolve to their section so the rail always shows where you are.
        fun railItemFor(destinationId: Int): Int? = when (destinationId) {
            R.id.liveDataFragment -> R.id.liveDataFragment
            R.id.errorOverviewFragment,
            R.id.errorDetailFragment,
            R.id.askAiFragment -> R.id.errorOverviewFragment
            R.id.tripFragment -> R.id.tripFragment
            R.id.consoleFragment -> R.id.consoleFragment
            // Settings and About are separate top-level destinations with
            // their own buttons: each highlights only itself.
            R.id.settingsFragment -> R.id.settingsFragment
            R.id.aboutFragment -> R.id.aboutFragment
            else -> null
        }

        val firstRunDestinations = setOf(
            R.id.onboardingFragment,
            R.id.permissionsFragment,
            R.id.connectFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (rail != null) {
                // Rail layout: rail owns top-level navigation, and the rail
                // hides itself during the first-run flow.
                rail.visibility =
                    if (destination.id in firstRunDestinations) View.GONE else View.VISIBLE
                val checked = railItemFor(destination.id)
                if (checked != null) {
                    rail.menu.findItem(checked)?.isChecked = true
                } else {
                    for (i in 0 until rail.menu.size()) {
                        rail.menu.getItem(i).isChecked = false
                    }
                }
            } else {
                // Phone layout: the in-flow top bar replaces the old overlaid
                // shortcut (which could cover fragment controls). It hides
                // during first-run; the selected icon tracks the destination.
                topBar?.visibility =
                    if (destination.id in firstRunDestinations) View.GONE else View.VISIBLE
                val selected = railItemFor(destination.id)
                val selectedColor = getColor(R.color.colorSecondary)
                val idleColor = getColor(R.color.on_surface)
                topNavButtons.forEachIndexed { index, button ->
                    val active = topNavDestinations[index] == selected
                    button.imageTintList =
                        android.content.res.ColorStateList.valueOf(
                            if (active) selectedColor else idleColor
                        )
                }
            }
        }

        topNavButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val destId = topNavDestinations[index]
                if (navController.currentDestination?.id != destId &&
                    !navController.popBackStack(destId, false)
                ) {
                    navController.navigate(destId)
                }
            }
        }

        val railView = rail
        railView?.setOnItemSelectedListener { item ->
            railView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // Pop back to an existing instance when possible so repeated
            // rail taps never stack duplicate destinations.
            if (navController.currentDestination?.id != item.itemId &&
                !navController.popBackStack(item.itemId, false)
            ) {
                navController.navigate(item.itemId)
            }
            true
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
