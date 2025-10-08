package com.gluedin.media.and

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gluedin.GluedInInitializer
import com.gluedin.callback.GIAssetCallback
import com.gluedin.callback.GIInitCallback
import com.gluedin.callback.GISdkCallback
import com.gluedin.callback.PaymentMethod
import com.gluedin.callback.PaymentStatus
import com.gluedin.callback.SDKInitStatus
import com.gluedin.callback.UserAction
import com.gluedin.callback.UserAuthStatus
import com.gluedin.data.network.response.SDKInitException
import com.gluedin.domain.entities.config.ShareData
import com.gluedin.domain.entities.curation.RailItem
import com.gluedin.domain.entities.curation.WidgetData
import com.gluedin.domain.entities.feed.VideoInfo
import com.gluedin.domain.entities.feed.ads.AdsRequestParams
import com.gluedin.domain.entities.feed.ads.AdsType
import com.gluedin.domain.entities.feed.series.transaction.SeriesTransactionRequest
import com.gluedin.exception.GluedInSdkException
import com.gluedin.feed.R
import com.gluedin.media.and.databinding.ActivityMainBinding
import com.gluedin.media.and.fragment.HomeFragment
import com.gluedin.media.and.payment.BillingManager
import com.gluedin.media.and.shopify.ShopifyCartManager
import com.gluedin.media.and.shopify.ViewCartActivity
import com.gluedin.media.and.shopify.WebViewActivity
import com.gluedin.presentation.enum.ContentType
import com.gluedin.presentation.utils.DataUtility
import com.gluedin.presentation.utils.DialogUtil
import com.gluedin.usecase.constants.GluedInConstants
import com.gluedin.view.BannerAdView
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

// Main Activity that hosts HomeFragment and initializes GluedIn SDK
class MainActivity : AppCompatActivity(), HostActivityCallback {

    private var gluedInConfigurations: GluedInInitializer.Configurations? = null
    private lateinit var binding: ActivityMainBinding
    private var localIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show progress while SDK initializes
        binding.progressBar.isVisible = true
        applySystemBottomMargin()
        // Start SDK initialization silently
        launchSDKSilently()
    }

    fun applySystemBottomMargin() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = bottomInset
            view.layoutParams = params
            insets
        }
    }


    // Initialize HomeFragment and setup UI
    private fun init() {
        val fragment = HomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainerView.id, fragment)
            .commit()

        binding.progressBar.isVisible = false

        // Example button click to launch SDK manually
        binding.thirdItem.setOnClickListener {
            launch(null, null, null, null, GluedInConstants.CarouselType.NONE,false)
        }
    }

    // Silent SDK initialization with callbacks
    private fun launchSDKSilently() {
        // Build GluedIn SDK configuration
        gluedInConfigurations = GluedInInitializer.Configurations.Builder()
            .setSdkCallback(mGISdkCallback)
            .setSdkInitCallback(mGIInitCallback)
            .setGIAssetCallback(giECommerceCallback)
            .setLogEnabled(true, Log.DEBUG)
            .setApiAndSecret(AppConstants.API_KEY, AppConstants.SECRET_KEY)
            .setFeedType(GluedInInitializer.Configurations.FeedType.VERTICAL)
            .setPreferredLanguage(AppConstants.APP_LANGUAGE)
            .setUserInfo("email_id", "password", "Full Name", "url_of_the_profile_pic")
            .create()

        // Validate and launch SDK
        gluedInConfigurations?.validateGluedInSDK(this)
    }

    // Required for HostActivityCallback
    override fun getGluedInConfigurations(): GluedInInitializer.Configurations? {
        return gluedInConfigurations
    }

    // Called when a user clicks on a rail item
    override fun launchSDK(item: RailItem, railItems: WidgetData) {
        lifecycleScope.launch {
            var carouselType = GluedInConstants.CarouselType.NONE
            var listOfData: List<String> = emptyList()
            var selectedRailId: String = ""
            var seriesId: String = ""
            var episodeNumber: Int = -1
            val deviceId = UUID.randomUUID().toString()
            var isDisableFullSDK : Boolean = false

            // Different content types → handle differently
            if (railItems.contentType == ContentType.STORY.value) {
                listOfData = DataUtility.getCarouselByStoryId(railItems)
                carouselType = GluedInConstants.CarouselType.STORY
                selectedRailId = item.assetId
                isDisableFullSDK = true
            } else if (railItems.contentType == ContentType.SERIES.value) {
                seriesId = item.id
                episodeNumber = -1
                isDisableFullSDK = false
            } else {
                listOfData = DataUtility.getCarouselById(railItems)
                selectedRailId = item.id
                isDisableFullSDK = false
            }

            launch(seriesId, episodeNumber, selectedRailId, listOfData, carouselType,isDisableFullSDK)
        }
    }

    // Launch GluedIn SDK with full configuration
    private fun launch(
        seriesId: String?,
        episodeNumber: Int?,
        selectedRailId: String?,
        railContentIds: List<String>?,
        carouselType: GluedInConstants.CarouselType,
        isDisableFullSDK : Boolean
    ) {
        // Build new SDK configuration for this launch
        gluedInConfigurations = GluedInInitializer.Configurations.Builder()
            .setSdkCallback(mGISdkCallback)
            .setSdkInitCallback(mGIInitCallback)
            .setGIAssetCallback(giECommerceCallback)
            .setDiscoverAsHome(true, GluedInConstants.HeaderType.EMBEDDED)
            .setApiAndSecret(AppConstants.API_KEY, AppConstants.SECRET_KEY)
            .setFeedType(GluedInInitializer.Configurations.FeedType.VERTICAL)
            .setPreferredLanguage(AppConstants.APP_LANGUAGE)
            .setUserInfo("email_id", "password", "Full Name", "url_of_the_profile_pic")
            .enableBottomBar(true)
            .enableBackButton(true)
            .setCarouselDetails(carouselType, selectedRailId, railContentIds ?: emptyList(), isDisableFullSDK)
            .setSeriesInfo(seriesId, episodeNumber)
            .create()

        gluedInConfigurations?.validateAndLaunchGluedInSDK(
            this,
            GluedInConstants.LaunchType.APP,
            intent
        )
    }

    val mGIInitCallback = object : GIInitCallback {
        // Callback for SDK lifecycle

        override fun onSDKLifecycle(
            mSDKInitStatus: SDKInitStatus,
            gluedInSdkException: GluedInSdkException?
        ) {
            when (mSDKInitStatus) {
                SDKInitStatus.SDK_INIT -> {
                    if (mSDKInitStatus.value) {
                        init()
                    }
                }

                SDKInitStatus.SDK_EXIT -> {
                    Toast.makeText(this@MainActivity, "SDK_EXIT", Toast.LENGTH_SHORT).show()
                }

                SDKInitStatus.SDK_AUTH -> {
                    Toast.makeText(this@MainActivity, "USER_AUTH", Toast.LENGTH_SHORT).show()
                }

                SDKInitStatus.SDK_RE_INIT_NEEDED -> TODO()
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
                    Toast.makeText(this@MainActivity, "user logout", Toast.LENGTH_SHORT).show()
                }
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

        override fun onUserProfileClick(userId: String) {
            Toast.makeText(this@MainActivity, "User Profile Click -> $userId", Toast.LENGTH_SHORT)
                .show()
        }

        override fun onAdsRequest(adsType: AdsType, adsRequestParams: AdsRequestParams): Fragment? {
            return null
        }

        override fun onDiscoverAdsRequest(
            adsType: AdsType,
            adsRequestParams: AdsRequestParams,
            view: BannerAdView
        ) {

        }

        override fun onRewardClick() {
            Toast.makeText(this@MainActivity, "Reward Clicked", Toast.LENGTH_SHORT).show()
        }

        override fun onWatchNowAction(deeplink: String) {
            TODO("Handle Watch Now deeplink")
        }

        override fun onInitiateSeriesPurchase(
            paymentMethod: PaymentMethod,
            inAppSkuId: String?,
            purchaseUrl: String?,
            seriesId: String?,
            episodeNumber: Int,
            onNotifyPaymentResult: (PaymentStatus) -> Unit
        ) {
            // Handle payments (in-app or subscription)
            if (PaymentMethod.IN_APP_PURCHASE == paymentMethod) {
                BillingManager.init(
                    activity = this@MainActivity,
                    skuId = inAppSkuId.orEmpty(),
                    seriesId = seriesId,
                    onNotifyPaymentResult
                )
            } else if (PaymentMethod.SUBSCRIPTION_PLAN == paymentMethod) {
                // Open browser with subscription deeplink
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(purchaseUrl.orEmpty()))
                try {
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    DialogUtil.showToast(
                        this@MainActivity,
                        getString(R.string.gluedin_common_invalid_url)
                    )
                }
            } else {
                Toast.makeText(this@MainActivity, "Other Payment Method", Toast.LENGTH_SHORT).show()
            }
        }
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
                ShopifyCartManager.init(this@MainActivity)
                callback?.let {
                    ShopifyCartManager.showProductDetails(
                        context, assetId.toString(),
                        it
                    )
                }

            }
        }

        override fun navigateToCart() {
            val intent = Intent(this@MainActivity, ViewCartActivity::class.java)
            startActivity(intent)
        }

        override fun getCartItemCount(callback: (Int) -> Unit) {
            ShopifyCartManager.init(this@MainActivity)
            ShopifyCartManager.getTotalCartItems() { cartCount ->
                callback(cartCount)
            }
        }

        override fun showOrderHistory() {
            ShopifyCartManager.init(this@MainActivity)
            val orderHistory = ShopifyCartManager.getOderHistoryUrl()
            val intent = Intent(this@MainActivity, WebViewActivity::class.java)
            intent.putExtra("url", orderHistory)
            intent.putExtra("title", "My Orders")
            startActivity(intent)
        }

    }

}
