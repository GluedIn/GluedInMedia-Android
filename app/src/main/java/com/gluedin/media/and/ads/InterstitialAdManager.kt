package com.gluedin.media.and.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Interstitial Ad Object Class
 * Using Google AdMob SDK
 */
object InterstitialAdManager {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    /**
     * Call this ONE function anywhere
     * It will:
     * 1. Show ad instantly if already loaded
     * 2. Load and then show when ready
     * 3. Continue app flow if ad fails
     */
    fun loadAndShow(
        activity: Activity,
        adUnitId: String,
        onAdClosed: (() -> Unit)? = null
    ) {
        // Ad already ready → show immediately
        interstitialAd?.let { ad ->
            showAd(activity, adUnitId, ad, onAdClosed)
            return
        }

        // Ad is loading → skip waiting
        if (isLoading) {
            onAdClosed?.invoke()
            return
        }

        // Load ad
        isLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            activity,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                    showAd(activity, adUnitId, ad, onAdClosed)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                    onAdClosed?.invoke()
                }
            }
        )
    }

    private fun showAd(
        activity: Activity,
        adUnitId: String,
        ad: InterstitialAd,
        onAdClosed: (() -> Unit)?
    ) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preload(activity, adUnitId)
                onAdClosed?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                onAdClosed?.invoke()
            }
        }

        ad.show(activity)
    }

    private fun preload(context: Context, adUnitId: String) {
        if (isLoading || interstitialAd != null) return

        isLoading = true
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                }
            }
        )
    }
}