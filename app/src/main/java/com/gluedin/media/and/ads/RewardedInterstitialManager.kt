package com.gluedin.media.and.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

object RewardedInterstitialManager {

    private const val TAG = "RewardedInterstitialAd"

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false

    /**
     * Loads a Rewarded Interstitial Ad.
     *
     * @param context  the Context
     * @param adUnitId optional Ad Unit ID (default: test ID)
     * @param onLoaded callback when ad successfully loads
     * @param onFailed callback when ad fails to load
     */
    fun load(
        context: Context,
        adUnitId: String,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        if (isLoading) {
            return
        }

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedInterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedInterstitialAd = null
                    isLoading = false
                    onFailed?.invoke(adError.message)
                }
            }
        )
    }

    /**
     * Shows the Rewarded Interstitial Ad.
     *
     * @param activity the Activity to show the ad in
     * @param onReward callback when user earns reward
     * @param onClosed callback when ad is closed/dismissed
     * @param onFailed callback when ad fails to show
     */
    fun show(
        activity: Activity,
        adId : String,
        onReward: ((RewardItem) -> Unit)? = null,
        onClosed: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        val ad = rewardedInterstitialAd
        if (ad == null) {
            onFailed?.invoke("Ad not ready")
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                rewardedInterstitialAd = null // Prevent reuse
            }

            override fun onAdDismissedFullScreenContent() {
                onClosed?.invoke()
                // Optionally reload after dismissal
                load(activity.applicationContext,adId)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedInterstitialAd = null
                onFailed?.invoke(adError.message)
            }
        }

        ad.show(activity) { rewardItem ->
            onReward?.invoke(rewardItem)
        }
    }

    /**
     * Checks whether an ad is ready to be shown.
     */
    fun isAdReady(): Boolean {
        return rewardedInterstitialAd != null
    }

    /**
     * Destroys ad reference to free memory.
     */
    fun clear() {
        rewardedInterstitialAd = null
        isLoading = false
    }
}