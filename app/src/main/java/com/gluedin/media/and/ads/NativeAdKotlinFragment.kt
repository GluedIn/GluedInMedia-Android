package com.gluedin.media.and.ads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.facebook.drawee.view.SimpleDraweeView
import com.gluedin.base.presentation.customView.GIAdsFragment
import com.gluedin.domain.entities.feed.ads.AdsRequestParams
import com.gluedin.domain.entities.feed.ads.NativeAdsType
import com.gluedin.media.and.R
import com.gluedin.media.and.databinding.NativeAdsVerticalBinding
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeCustomFormatAd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.iterator

class NativeAdKotlinFragment(
    private var adsType: NativeAdsType? = null,
    private var adsRequestParams: AdsRequestParams? = null,
    private var context: Context? = null
) : GIAdsFragment() {
    private var binding: NativeAdsVerticalBinding? = null
    private var videoController: VideoController? = null
    private var nativeCustomFormatAd: NativeCustomFormatAd? = null
    private var nativeAd: NativeAd? = null


    init {
        if (adsType?.name == NativeAdsType.AD_MOB_NATIVE.name) {
            loadAds()
        } else {
            loadGamAds(false)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = NativeAdsVerticalBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        if (adsType?.name == NativeAdsType.AD_MOB_NATIVE.name) {
            nativeAd?.mediaContent?.videoController?.pause()
        } else {
            videoController?.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        // Ensure playback is fully stopped
        videoController?.stop()
    }

    override fun onResume() {
        super.onResume()
        if (adsType?.name == NativeAdsType.AD_MOB_NATIVE.name) {
            binding?.adMob?.plusFeedAdsContainer?.visibility = View.VISIBLE
            binding?.adGam?.plusFeedAdsContainer?.visibility = View.GONE
            nativeAd?.let { populateNativeAdsView(it) }
            binding?.feedShimmer?.root?.isVisible = false
        } else {
            binding?.adMob?.plusFeedAdsContainer?.visibility = View.GONE
            binding?.adGam?.plusFeedAdsContainer?.visibility = View.VISIBLE
            if (nativeCustomFormatAd != null) {
                if (videoController != null) {
                    videoController?.play()
                } else {
                    nativeCustomFormatAd?.let { displayCustomFormatAd(it) }
                }
            } else {
                loadGamAds(true)
            }
        }
    }

    /**
     * Main cleanup point for ads, video, and binding
     */
    override fun onDestroyView() {
        super.onDestroyView()

        // Stop and clear video controller
        videoController?.stop()
        videoController = null

        // Destroy AdMob ad
        nativeAd?.destroy()
        nativeAd = null

        // Destroy GAM ad
        nativeCustomFormatAd?.destroy()
        nativeCustomFormatAd = null

        // Clear ad views to prevent leaks
        binding?.adGam?.adFrame?.removeAllViews()

        // Remove touch listeners
        binding?.adGam?.adClickArea?.setOnTouchListener(null)

        // Clear binding
        binding?.unbind()
        binding = null
    }

    /** Loading AdMob ads **/
    private fun loadAds() {
        context?.let { mContext ->
            val adMobUnitId = adsRequestParams?.adsId.orEmpty()

            // Custom parameters
            val extras = Bundle()
            for ((key, value) in adsRequestParams?.configCustomParams ?: emptyMap()) {
                extras.putString(key, value)
            }

            val adRequest = AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()

            val builder = AdLoader.Builder(mContext, adMobUnitId)
            builder.forNativeAd { nativeAds: NativeAd? ->
                if (nativeAds == null) return@forNativeAd
                setAdLoadStatus(true)
                nativeAd = nativeAds
            }

            val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
            val adOptions = NativeAdOptions.Builder().setVideoOptions(videoOptions).build()
            builder.withNativeAdOptions(adOptions)

            val adLoader = builder.withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    setAdLoadStatus(false)
                }

                override fun onAdLoaded() {
                    super.onAdLoaded()
                    setAdLoadStatus(true)
                }
            }).build()

            adLoader.loadAd(adRequest)
        }
    }

    /** Populate AdMob native ads **/
    private fun populateNativeAdsView(nativeAd: NativeAd) {
        val adView = binding?.adMob?.adView ?: return

        // Set views
        adView.mediaView = adView.findViewById(R.id.ad_media)
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.priceView = adView.findViewById(R.id.ad_price)
        adView.starRatingView = adView.findViewById(R.id.ad_stars)
        adView.storeView = adView.findViewById(R.id.ad_store)
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

        (adView.headlineView as TextView).text = nativeAd.headline
        adView.mediaView?.mediaContent = nativeAd.mediaContent

        // Body
        if (nativeAd.body.isNullOrEmpty()) {
            adView.bodyView?.visibility = View.GONE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView).text = nativeAd.body
        }

        // Call to action
        if (nativeAd.callToAction.isNullOrEmpty()) {
            adView.callToActionView?.visibility = View.GONE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button).text = nativeAd.callToAction
        }

        // Icon
        nativeAd.icon?.let {
            (adView.iconView as ImageView).setImageDrawable(it.drawable)
            adView.iconView?.visibility = View.VISIBLE
        } ?: run { adView.iconView?.visibility = View.GONE }

        // Star rating
        nativeAd.starRating?.let {
            (adView.starRatingView as RatingBar).rating = it.toFloat()
            adView.starRatingView?.visibility = View.VISIBLE
        } ?: run { adView.starRatingView?.visibility = View.GONE }

        // Advertiser
        if (nativeAd.advertiser.isNullOrEmpty()) {
            adView.advertiserView?.visibility = View.GONE
        } else {
            adView.advertiserView?.visibility = View.VISIBLE
            (adView.advertiserView as TextView).text = nativeAd.advertiser
        }

        adView.setNativeAd(nativeAd)
    }

    /** Loading GAM ads **/
    private fun loadGamAds(isReloaded: Boolean) {
        context?.let { mContext ->
            val adUnitId = adsRequestParams?.adsId.orEmpty()
            val customFormatList = adsRequestParams?.adFormatId ?: emptyList()

            val builder = AdLoader.Builder(mContext, adUnitId)

            for (customFormatId in customFormatList) {
                builder.forCustomFormatAd(customFormatId, { ad ->
                    setAdLoadStatus(true)
                    nativeCustomFormatAd = ad
                    if (isReloaded) {
                        displayCustomFormatAd(ad)
                    }
                }) { _, _ ->
                    // handle click
                }
            }

            val newRequest = AdManagerAdRequest.Builder()

            // GAM custom targeting params
            adsRequestParams?.gamCustomParams?.forEach { (key, value) ->
                newRequest.addCustomTargeting(key, value)
            }

            // Local custom params
            adsRequestParams?.configCustomParams?.forEach { (key, value) ->
                newRequest.addCustomTargeting(key, value)
            }

            val videoOptions = VideoOptions.Builder()
                .setStartMuted(false)
                .setCustomControlsRequested(true)
                .build()

            val adOptions = NativeAdOptions.Builder()
                .setVideoOptions(videoOptions)
                .build()

            builder.withNativeAdOptions(adOptions)

            val adLoader = builder.withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    setAdLoadStatus(false)
                }

                override fun onAdLoaded() {
                    super.onAdLoaded()
                    setAdLoadStatus(true)
                }
            }).build()

            adLoader.loadAd(newRequest.build())
        }
    }

    /** Display GAM ads **/
    private fun displayCustomFormatAd(nativeCustomFormatAd: NativeCustomFormatAd) {
        if (binding == null || !isAdded) return
        val mediaContent = nativeCustomFormatAd.mediaContent
        setGAMText(nativeCustomFormatAd)

        if (mediaContent != null && mediaContent.hasVideoContent()) {
            videoController = mediaContent.videoController
            videoController?.videoLifecycleCallbacks =
                object : VideoController.VideoLifecycleCallbacks() {
                    override fun onVideoEnd() {
                        super.onVideoEnd()
                        videoController?.play()
                    }

                    override fun onVideoPlay() {
                        super.onVideoPlay()
                        if (!isVisible) videoController?.pause()
                    }

                    override fun onVideoStart() {
                        super.onVideoStart()
                        binding?.feedShimmer?.root?.isVisible = false
                        if (!isVisible) videoController?.pause()
                    }
                }

            val mediaView = MediaView(requireContext())
            mediaView.mediaContent = mediaContent
            binding?.adGam?.adFrame?.addView(mediaView)
            setGestureInGam(videoController)
        } else {
            val mainImage = SimpleDraweeView(requireContext())
            mainImage.adjustViewBounds = true
            mainImage.setImageDrawable(nativeCustomFormatAd.getImage("MainImage")?.drawable)
            binding?.adGam?.adFrame?.addView(mainImage)
        }
    }

    private fun setGAMText(nativeCustomFormatAd: NativeCustomFormatAd) {
        binding?.adGam?.apply {
            gamName.text = nativeCustomFormatAd.getText("Headline")
            gamTitle.text = nativeCustomFormatAd.getText("headline")
            gamDescription.text = nativeCustomFormatAd.getText("Caption")

            val imageUrl = nativeCustomFormatAd.getText("logo").toString()
            plusSawFeedUserProfile.isVisible = imageUrl.isNotEmpty() && imageUrl != "null"
            if (plusSawFeedUserProfile.isVisible) {
                plusSawFeedUserProfile.setImageURI(imageUrl)
            }

            val ctaText = nativeCustomFormatAd.getText("CTAText")
            gamProductActionBtn.text = ctaText
            gamProductActionBtn.isVisible = !ctaText.isNullOrEmpty()

            gamProductActionBtn.setOnClickListener {
                gamProductActionBtn.disable()
                lifecycleScope.launch {
                    delay(1000)
                    gamProductActionBtn.enable()
                }
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(nativeCustomFormatAd.getText("TextURL").toString())
                )
                try {
                    requireContext().startActivity(browserIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            gamSponsored.isVisible = true
        }
    }

    private fun setGestureInGam(videoController: VideoController?) {
        val gestureDetector =
            GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (VideoController.PLAYBACK_STATE_PLAYING == videoController?.playbackState) {
                        videoController.pause()
                        binding?.adGam?.plusSawFeedPlay?.isVisible = true
                    } else {
                        videoController?.play()
                        binding?.adGam?.plusSawFeedPlay?.isVisible = false
                    }
                    return true
                }

                override fun onDown(e: MotionEvent): Boolean = true
            })

        binding?.adGam?.adClickArea?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun View.disable() {
        this.isClickable = false
    }

    private fun View.enable() {
        this.isClickable = true
    }
}
