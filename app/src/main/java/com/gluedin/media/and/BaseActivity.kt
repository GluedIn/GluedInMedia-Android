package com.gluedin.media.and

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.android.billingclient.api.BillingClient
import com.gluedin.GluedInInitializer
import com.gluedin.callback.AdsStatus
import com.gluedin.callback.GIAdsCallback
import com.gluedin.callback.GIAssetCallback
import com.gluedin.callback.GIInitCallback
import com.gluedin.callback.GIPaymentCallback
import com.gluedin.callback.GISdkCallback
import com.gluedin.callback.PaymentMethod
import com.gluedin.callback.PaymentStatus
import com.gluedin.callback.SDKInitStatus
import com.gluedin.callback.SubscriptionDetails
import com.gluedin.callback.UserAction
import com.gluedin.callback.UserAuthStatus
import com.gluedin.domain.entities.config.ShareData
import com.gluedin.domain.entities.feed.VideoInfo
import com.gluedin.domain.entities.feed.ads.AdsRequestParams
import com.gluedin.domain.entities.feed.ads.BannerAdsType
import com.gluedin.domain.entities.feed.ads.InterstitialAdsType
import com.gluedin.domain.entities.feed.ads.NativeAdsType
import com.gluedin.exception.GluedInSdkException
import com.gluedin.media.and.ads.BannerAdLoader
import com.gluedin.media.and.ads.InterstitialAdManager
import com.gluedin.media.and.ads.NativeAdKotlinFragment
import com.gluedin.media.and.ads.RewardedInterstitialManager
import com.gluedin.media.and.payment.BillingManager
import com.gluedin.media.and.shopify.ShopifyCartManager
import com.gluedin.media.and.shopify.ViewCartActivity
import com.gluedin.media.and.shopify.WebViewActivity
import com.gluedin.usecase.constants.GluedInConstants
import com.gluedin.view.BannerAdView
import timber.log.Timber

abstract class BaseActivity : AppCompatActivity() {
    var sdkConfigurations: GluedInInitializer.Configurations? = null
    abstract fun init()

    // Silent SDK initialization with callbacks
    fun launchSDKSilently() {
        // Build GluedIn SDK configuration
        sdkConfigurations =
            GluedInInitializer.Configurations.Builder().setSdkCallback(mGISdkCallback)
                .setSdkInitCallback(mGIInitCallback)
                .setGIAssetCallback(giECommerceCallback)
                .setLogEnabled(true, Log.DEBUG).setHttpLogEnabled(true, 3)
                .setApiAndSecret(AppConstants.API_KEY, AppConstants.SECRET_KEY)
                .setPreferredLanguage(AppConstants.APP_LANGUAGE)
                //.setUserInfo("email_id", "password", "Full Name", "url_of_the_profile_pic")
                .create()

        // Validate and launch SDK
        sdkConfigurations?.validateGluedInSDK(this)
    }


    // Launch GluedIn SDK with full configuration
    fun launch(
        seriesId: String?,
        episodeNumber: Int?,
        selectedRailId: String?,
        railContentIds: List<String>?,
        carouselType: GluedInConstants.CarouselType,
        isDisableFullSDK: Boolean
    ) {
        // Build new SDK configuration for this launch
        sdkConfigurations =
            GluedInInitializer.Configurations.Builder().setSdkCallback(mGISdkCallback)
                .setSdkInitCallback(mGIInitCallback).setGIAssetCallback(giECommerceCallback)
                .setGIPaymentCallback(giPaymentCallBack).setGIAdsCallback(giAdsCallBack)
                // need to remove
                .setLogEnabled(true, Log.DEBUG).setHttpLogEnabled(true, 3)
                .setDiscoverAsHome(true, GluedInConstants.HeaderType.EMBEDDED)
                .setApiAndSecret(AppConstants.API_KEY, AppConstants.SECRET_KEY)
                .setPreferredLanguage(AppConstants.APP_LANGUAGE)
                //.setUserInfo("email_id", "password", "Full Name", "url_of_the_profile_pic")
                .enableBottomBar(true).enableBackButton(true).setCarouselDetails(
                    carouselType, selectedRailId, railContentIds ?: emptyList(), isDisableFullSDK
                ).setSeriesInfo(seriesId, episodeNumber).create()

        sdkConfigurations?.validateAndLaunchGluedInSDK(
            this, GluedInConstants.LaunchType.APP, intent
        )
    }


    val mGIInitCallback = object : GIInitCallback {
        // Callback for SDK lifecycle

        override fun onSDKLifecycle(
            mSDKInitStatus: SDKInitStatus, gluedInSdkException: GluedInSdkException?
        ) {
            when (mSDKInitStatus) {
                SDKInitStatus.SDK_INIT -> {
                    if (mSDKInitStatus.value) {
                        init()
                    }
                    else {
                        Timber.d("%s%s", "Failed to load the GluedInSDK: " + mSDKInitStatus.value + ", ", mSDKInitStatus.name)
                        Toast.makeText(this@BaseActivity, "SDK Init Failed. Use the correct API & Secret to initialise the SDK", Toast.LENGTH_SHORT).show()
                    }
                }

                SDKInitStatus.SDK_EXIT -> {
                    Toast.makeText(this@BaseActivity, "SDK_EXIT", Toast.LENGTH_SHORT).show()
                }

                SDKInitStatus.SDK_AUTH -> Unit


                SDKInitStatus.SDK_RE_INIT_NEEDED ->  Unit
            }
        }
    }

    // Callback for SDK events (auth, sharing, ads, profile, purchases)
    val mGISdkCallback = object : GISdkCallback {
        override fun onUserAuthStatus(userAuthStatus: UserAuthStatus, currentVideo: VideoInfo?) {
            when (userAuthStatus) {
                UserAuthStatus.USER_LOGIN_REQUIRED -> {
                    GluedInInitializer.closeSDK()
                    // Redirect to your login screen if needed
                }

                UserAuthStatus.USER_LOGOUT -> {
                    Toast.makeText(this@BaseActivity, "user logout", Toast.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }

        override fun onShareAction(shareData: ShareData) {
            val message = "https://gluedin.page.link/data?${shareData.deeplink}"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain" // ✅ Correct way to set MIME type
                putExtra(Intent.EXTRA_TEXT, message)
            }
            val chooser = Intent.createChooser(intent, "Share via")
            startActivity(chooser)
            Timber.d(shareData.deeplink)
        }

        override fun onUserProfileClick(userId: String)=  Unit

        override fun onRewardClick()  =  Unit

        override fun onWatchNowAction(deeplink: String) =  Unit

    }


    // Callback for E-Commerce actions
    val giECommerceCallback = object : GIAssetCallback {

        override fun onUserAction(
            context: Context,
            action: UserAction,
            assetId: String?,
            eventRefId: Int?,
            callback: ((Int) -> Unit)?
        ) {
            if (UserAction.ADD_TO_CART == action) {
                ShopifyCartManager.init(this@BaseActivity)
                callback?.let {
                    ShopifyCartManager.showProductDetails(
                        context, assetId.toString(), it
                    )
                }

            }
        }

        override fun navigateToCart() {
            val intent = Intent(this@BaseActivity, ViewCartActivity::class.java)
            startActivity(intent)
        }

        override fun getCartItemCount(callback: (Int) -> Unit) {
            ShopifyCartManager.init(this@BaseActivity)
            ShopifyCartManager.getTotalCartItems() { cartCount ->
                callback(cartCount)
            }
        }

        override fun showOrderHistory() {
            ShopifyCartManager.init(this@BaseActivity)
            val orderHistory = ShopifyCartManager.getOderHistoryUrl()
            val intent = Intent(this@BaseActivity, WebViewActivity::class.java)
            intent.putExtra("url", orderHistory)
            intent.putExtra("title", "My Orders")
            startActivity(intent)
        }

    }

    val giAdsCallBack = object : GIAdsCallback {
        override fun onNativeRequest(
            adsType: NativeAdsType, adsRequestParams: AdsRequestParams
        ): Fragment? {
            when (adsType) {
                NativeAdsType.AD_MOB_NATIVE, NativeAdsType.GAM_NATIVE -> {
                    return NativeAdKotlinFragment(
                        adsType, adsRequestParams, this@BaseActivity.application
                    )
                }
            }
        }

        override fun onBannerAdsRequest(
            adsType: BannerAdsType, adsRequestParams: AdsRequestParams, view: BannerAdView?
        ) {
            view?.let {
                BannerAdLoader(view.context, it, adsType, adsRequestParams).loadAd()
            }

        }

        override fun onInterstitialAdsRequest(
            adsType: InterstitialAdsType, adsRequestParams: AdsRequestParams
        ) {
            InterstitialAdManager.loadAndShow(
                this@BaseActivity, adsRequestParams.adsId
            )
        }

    }
    val giPaymentCallBack = object : GIPaymentCallback {


        override fun onInitiateSeriesPurchase(
            paymentMethod: PaymentMethod,
            inAppSkuId: String?,
            basePlanId: String?,
            offerId: String?,
            purchaseUrl: String?,
            seriesId: String?,
            episodeNumber: Int,
            packageId: String,
            userId: String,
            onNotifyPaymentResult: (status: PaymentStatus, String, String, PaymentMethod) -> Unit
        ) {

            // Handle payments (in-app or subscription)
            if (PaymentMethod.IN_APP_PURCHASE == paymentMethod) {
                /*
                  //TODO:  Enable this method for the actual use case.
                  callBillingManager(
                          inAppSkuId,
                          basePlanId,
                          purchaseUrl,
                          seriesId,
                          packageId,
                          userId,
                          paymentMethod,
                          onNotifyPaymentResult
                      )
                 */

                Toast.makeText(
                    this@BaseActivity,
                    "In-app purchases haven’t been configured yet.",
                    Toast.LENGTH_SHORT
                ).show()

            } else if (PaymentMethod.SUBSCRIPTION_PLAN == paymentMethod) {
            /*
                 //TODO: Enable this method for the actual use case.
                   callBillingManagerForSubscription(
                                    inAppSkuId,
                                    basePlanId,
                                    purchaseUrl,
                                    seriesId,
                                    packageId,
                                    userId,
                                    paymentMethod,
                                    onNotifyPaymentResult,
                                )
                */
                Toast.makeText(
                    this@BaseActivity,
                    "Subscription hasn’t been configured yet.",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (PaymentMethod.PAYMENT_GATEWAY == paymentMethod) {
                // Open browser with subscription deeplink
                Toast.makeText(
                    this@BaseActivity,
                    "onSelectPaymentMethod seriesId :$seriesId \n paymentUrl : $purchaseUrl \n deeplink : $purchaseUrl",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this@BaseActivity, "Other Payment Method", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onRewardedAdRequested(
            adUnitID: String,
            adsType: String,
            seriesId: String?,
            onNotifyAdsResult: (AdsStatus) -> Unit
        ) {
            rewardedInterstitialAd(adUnitID, adsType, seriesId, onNotifyAdsResult)
        }

        override fun onProductDetailsFetched(
            inAppSkuId: List<String>?,
            paymentMethod: PaymentMethod,
            onNotifyPriceResult: (Map<String, Any?>) -> Unit
        ) {
            if (PaymentMethod.IN_APP_PURCHASE == paymentMethod) {
                /* TODO: Enable this method to initiate Purchase from Google Play Store. */

//                 inAppSkuId?.let {
//                       fetchPricePartsForSkus(
//                           inAppSkuId,
//                           BillingClient.ProductType.INAPP,
//                           onNotifyPriceResult
//                       )
//                   }
            } else if (PaymentMethod.SUBSCRIPTION_PLAN == paymentMethod) {
                /* TODO: Enable this method to initiate Purchase from Google Play Store. */

//                inAppSkuId?.let {
//                    fetchPricePartsForSkus(
//                        it,
//                        BillingClient.ProductType.SUBS,
//                        onNotifyPriceResult
//                    )
//                }
            }
        }

        override fun onManageSubscription(
            paymentMethod: PaymentMethod,
            inAppSkuId: String,
            userId: String,
            onNotifyResult: (status: Boolean) -> Unit
        ) {
            BillingManager.openPlayStoreSubscription(this@BaseActivity, inAppSkuId, packageName)
        }

        override fun onUpgradeSubscriptionList(
            inAppSkuId: String,
            paymentMethod: PaymentMethod,
            onNotifyPriceResult: (Map<String, SubscriptionDetails?>, String) -> Unit
        ) {
            if (PaymentMethod.SUBSCRIPTION_PLAN == paymentMethod) {
                BillingManager.fetchPricePartsForSkus(
                    activity = this@BaseActivity,
                    skuIds = listOf(inAppSkuId),
                    productType = BillingClient.ProductType.SUBS
                ) { resultMap ->
                    BillingManager.getActiveBasePlanId(inAppSkuId) { _ ->

                    }

                }
            }

        }
    }

    private fun rewardedInterstitialAd(
        adId: String,
        platformName: String,
        seriesId: String?,
        onNotifyAdsResult: (AdsStatus) -> Unit
    ) {
        RewardedInterstitialManager.load(context = this, adUnitId = adId, onLoaded = {
            RewardedInterstitialManager.show(
                activity = this,
                adId = adId,
                onReward = { _ -> },
                onClosed = {
                    onNotifyAdsResult.invoke(AdsStatus.AdsSuccess)
                },
                onFailed = { error ->
                    onNotifyAdsResult.invoke(AdsStatus.AdsFailed)
                },
            )
        }, onFailed = { _ ->
            onNotifyAdsResult.invoke(AdsStatus.AdsFailed)
        })
    }

    private fun fetchPricePartsForSkus(
        inAppSkuId: List<String>,
        productType: String,
        onNotifyPriceResult: (Map<String, Any?>) -> Unit
    ) {
        BillingManager.fetchPricePartsForSkus(
            activity = this, skuIds = inAppSkuId, productType = productType
        ) { resultMap ->

            runOnUiThread {
                // Directly return to callback
                onNotifyPriceResult(resultMap)
            }
        }
    }

    private fun callBillingManager(
        skuId: String?,
        basePlanId: String?,
        paymentUrl: String?,
        seriesId: String?,
        packageId: String?,
        userId: String?,
        paymentMethod: PaymentMethod,
        onNotifyPaymentResult: (PaymentStatus, String, String, PaymentMethod) -> Unit
    ) {
        BillingManager.init(
            activity = this,
            skuId = skuId.orEmpty(),
            basePlanId = basePlanId.orEmpty(),
            seriesId = seriesId,
            paymentUrl = paymentUrl,
            packageId = packageId,
            productType = BillingClient.ProductType.SUBS,
            userId = userId.toString(),
            paymentMethod,
            onNotifyPaymentResult,
        )

    }

    private fun callBillingManagerForSubscription(
        skuId: String?,
        basePlanId: String?,
        paymentUrl: String?,
        seriesId: String?,
        packageId: String?,
        userId: String?,
        paymentMethod: PaymentMethod,
        onNotifyPaymentResult: (PaymentStatus, String, String, PaymentMethod) -> Unit
    ) {
        BillingManager.init(
            activity = this,
            skuId = skuId.toString(),
            basePlanId = basePlanId.orEmpty(),
            seriesId = seriesId,
            paymentUrl = paymentUrl,
            packageId = packageId,
            productType = BillingClient.ProductType.SUBS,
            userId = userId.toString(),
            paymentMethod = paymentMethod,
            onNotifyPaymentResult
        )

    }
}