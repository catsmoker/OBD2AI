package com.catsmoker.obd2ai

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import java.util.Arrays

class MainActivity : AppCompatActivity() {

    lateinit var bluetoothHelper: BluetoothHelper
    lateinit var obdHelper: ObdHelper
    lateinit var openAIService: OpenAIService
    lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {
            val testDeviceIds = Arrays.asList("AB065C801A1B4DA9FCCDBC44E5483FDD")
            val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
            MobileAds.setRequestConfiguration(configuration)

            val adView = findViewById<AdView>(R.id.adView)
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)

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
        }

        bluetoothHelper = BluetoothHelper(this)
        obdHelper = ObdHelper(bluetoothHelper)
        openAIService = OpenAIService(this)
        firebaseAnalytics = Firebase.analytics

        val settingsButton = findViewById<ImageButton>(R.id.button_settings_global)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.settingsFragment) {
                settingsButton.visibility = View.GONE
            } else {
                settingsButton.visibility = View.VISIBLE
            }
        }

        settingsButton.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
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
