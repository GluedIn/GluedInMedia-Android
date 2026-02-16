package com.gluedin.media.and.ads

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import com.gluedin.domain.entities.feed.ads.AdsRequestParams
import com.gluedin.domain.entities.feed.ads.BannerAdsType
import com.gluedin.view.BannerAdView
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView

class BannerAdLoader(
    private val context: Context?,
    private val bannerAdView: BannerAdView,
    private val adsType: BannerAdsType,
    private val adsRequestParams: AdsRequestParams
) {
    fun loadAd() {
        when (adsType) {
            BannerAdsType.AD_MOB_BANNER -> loadAdMobAds()
            BannerAdsType.GAM_BANNER -> loadGamAds()
        }
    }

    private fun loadAdMobAds() {
        val extras = Bundle()
        for ((key, value) in adsRequestParams.configCustomParams ?: emptyMap()) {
            extras.putString(key, value)
        }

        val adView = context?.let { AdView(it) }.apply {
            this!!.setAdSize(AdSize.BANNER)
            adUnitId = adsRequestParams.adsId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    bannerAdView.visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    bannerAdView.visibility = View.GONE
                }
            }
        }

        val adRequest = AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()

        bannerAdView.removeAllViews()
        adView?.let { bannerAdView.setAdView(it) }
        adView?.loadAd(adRequest)
    }

    private fun loadGamAds() {
        val adView = context?.let { AdManagerAdView(it) }.apply {
            this?.adUnitId = adsRequestParams.adsId
            this!!.setAdSize(AdSize.BANNER)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    bannerAdView.isVisible = true
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    bannerAdView.isVisible = false
                }
            }
        }

        val adRequestBuilder = AdManagerAdRequest.Builder()
        for ((key, value) in adsRequestParams.configCustomParams ?: emptyMap()) {
            adRequestBuilder.addCustomTargeting(key, value)
        }

        bannerAdView.removeAllViews()
        adView?.let { bannerAdView.setAdView(it) }
        adView?.loadAd(adRequestBuilder.build())
    }

}
