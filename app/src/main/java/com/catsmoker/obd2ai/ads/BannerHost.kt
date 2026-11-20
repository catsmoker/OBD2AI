package com.catsmoker.obd2ai.ads

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.catsmoker.obd2ai.BuildConfig
import com.google.android.gms.ads.*

/** The measured slot owns one creative; stale SDK callbacks cannot replace its successor. */
internal class BannerHost(private val activity: Activity, private val slot: FrameLayout) {
    private var request: (() -> AdRequest)? = null
    private var banner: AdView? = null
    private var initializing = false
    private var ready = false
    private var closed = false

    init {
        slot.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ ->
            if (r - l != oldR - oldL) slot.post { render() }
        }
    }

    fun update(load: (() -> AdRequest)?) {
        request = load
        clear()
        if (closed || load == null || slot.isInEditMode) return
        slot.visibility = View.VISIBLE
        if (ready) slot.post { render() }
        else if (!initializing) {
            initializing = true
            try {
                MobileAds.initialize(activity) {
                    activity.runOnUiThread {
                        initializing = false
                        ready = true
                        render()
                    }
                }
            } catch (_: RuntimeException) {
                initializing = false
                clear()
            }
        }
    }

    private var width = 0
    private fun render() {
        val load = request ?: return
        if (closed || !ready || slot.visibility != View.VISIBLE || slot.width <= 0 ||
            (banner != null && width == slot.width)) return
        try {
            releaseView()
            width = slot.width
            // Use standard banner size (320x50dp) for a compact, fixed-height banner
            // that doesn't push content or cover too much screen space.
            val size = AdSize.BANNER
            val view = AdView(activity)
            banner = view
            view.adUnitId = BuildConfig.BANNER_ID
            view.setAdSize(size)
            view.adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (banner === view) clear()
                }
            }
            slot.addView(view, FrameLayout.LayoutParams(-1, size.getHeightInPixels(activity)))
            view.loadAd(load())
        } catch (_: RuntimeException) {
            clear()
        }
    }

    private fun releaseView() {
        val old = banner
        banner = null
        slot.removeAllViews()
        runCatching { old?.destroy() }
    }
    private fun clear() {
        releaseView()
        slot.visibility = View.GONE
    }
    fun pause() { banner?.pause() }
    fun resume() { banner?.resume() }
    fun close() { closed = true; request = null; clear() }
}
