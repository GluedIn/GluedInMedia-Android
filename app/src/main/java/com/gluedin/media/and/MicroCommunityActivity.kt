package com.gluedin.media.and

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.gluedin.domain.entities.challengeDetail.widgetConfig.WidgetConfigDetails
import com.gluedin.domain.entities.config.ShareData
import com.gluedin.domain.entities.feed.AssetsInformation
import com.gluedin.domain.entities.feed.VideoInfo
import com.gluedin.domain.entities.feed.ads.AdsRequestParams
import com.gluedin.domain.entities.feed.ads.AdsType
import com.gluedin.domain.entities.feed.series.transaction.SeriesTransactionRequest
import com.gluedin.exception.GluedInSdkException
import com.gluedin.feed.R
import com.gluedin.media.and.adapter.SampleVideoAdapter
import com.gluedin.media.and.adapter.SampleVideoListener
import com.gluedin.media.and.databinding.AppMicroCommunityBinding
import com.gluedin.presentation.utils.DialogUtil
import com.gluedin.presentation.utils.TimeUtility
import com.gluedin.presentation.utils.extensions.isNetworkAvailable
import com.gluedin.media.and.AppConstants.assetId
import com.gluedin.media.and.payment.BillingManager
import com.gluedin.media.and.shopify.ShopifyCartManager
import com.gluedin.media.and.shopify.ViewCartActivity
import com.gluedin.media.and.shopify.WebViewActivity
import com.gluedin.usecase.challengeDetail.WidgetInteractor
import com.gluedin.usecase.config.AppConfigInteractor
import com.gluedin.usecase.config.LaunchConfig
import com.gluedin.usecase.constants.GluedInConstants
import com.gluedin.view.BannerAdView
import kotlinx.coroutines.launch

class MicroCommunityActivity : AppCompatActivity(), SampleVideoListener {
    private lateinit var binding: AppMicroCommunityBinding
    private var gluedInConfigurations: GluedInInitializer.Configurations? = null
    private var widgetConfigDetails: WidgetConfigDetails? = null
    private var challengeEndDate: String? = null
    private var expireDays: Long = -1L
    private var adapterChallenge: SampleVideoAdapter? = null
    private var appConfigInteractor: AppConfigInteractor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AppMicroCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBottomMargin()
        binding.progressBar.isVisible = true
        initListener()
        launchSDKSilently()
    }

    private fun applySystemBottomMargin() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = bottomInset
            view.layoutParams = params
            insets
        }
    }

    private fun initListener() {

        binding?.layoutCreator?.setOnClickListener {
            if (widgetConfigDetails?.challengeInfo != null && expireDays < 0L) {
                showCreatorAlert()
                return@setOnClickListener
            }
            launch(GluedInConstants.EntryPoint.CREATOR, assetsInformation = getProductInfo())
        }


        binding?.layoutChallengeCreator?.setOnClickListener {
            binding?.layoutCreator?.performClick()
        }
        binding?.btnRewards?.setOnClickListener {
            launch(GluedInConstants.EntryPoint.REWARD)
        }

        binding?.btnViewLeaderboard?.setOnClickListener {
            launch(entryPoint = GluedInConstants.EntryPoint.LEADERBOARD)
        }

        binding?.ivBack?.setOnClickListener {
            finish()
        }
    }

    private fun launchSDKSilently() {
        binding?.mainLayout?.isVisible = false
        binding?.progressBar?.isVisible = true
        binding?.layoutChallengeCreator?.isVisible = false
        binding?.btnViewLeaderboard?.isVisible = false

        if (!isNetworkAvailable()) {
            showInternetUI()
            return
        }

        val mGIInitCallback = object : GIInitCallback {
            override fun onSDKLifecycle(
                mSDKInitStatus: SDKInitStatus,
                gluedInSdkException: GluedInSdkException?
            ) {
                when (mSDKInitStatus) {
                    SDKInitStatus.SDK_INIT -> {
                        if (mSDKInitStatus.value) {
                            initVariable()
                        } else {
                            binding?.progressBar?.isVisible = false
                            gluedInSdkException?.getErrorMessage()?.let {
                                DialogUtil.showToast(this@MicroCommunityActivity, it)
                            }
                        }
                    }

                    SDKInitStatus.SDK_EXIT -> {
                        Toast.makeText(this@MicroCommunityActivity, "SDK_EXIT", Toast.LENGTH_SHORT)
                            .show()
                    }

                    SDKInitStatus.SDK_AUTH -> {
                        Toast.makeText(this@MicroCommunityActivity, "USER_AUTH", Toast.LENGTH_SHORT)
                            .show()
                    }

                    SDKInitStatus.SDK_RE_INIT_NEEDED -> TODO()
                }
            }

        }

        val mGISdkCallback = object : GISdkCallback {

            override fun onUserAuthStatus(
                userAuthStatus: UserAuthStatus, currentVideo: VideoInfo?
            ) {
                when (userAuthStatus) {
                    UserAuthStatus.USER_LOGIN_REQUIRED -> {
                        Toast.makeText(
                            this@MicroCommunityActivity,
                            "user loggedin required",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }

                    UserAuthStatus.USER_LOGOUT -> {
                        Toast.makeText(
                            this@MicroCommunityActivity,
                            "user logout",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }
            }

            override fun onShareAction(shareData: ShareData) {
                Toast.makeText(
                    this@MicroCommunityActivity,
                    "User has click on share icon with share meta data",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onUserProfileClick(userId: String) {
                Toast.makeText(
                    this@MicroCommunityActivity,
                    "on User Profile Click -> $userId",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onAdsRequest(
                adsType: AdsType, adsRequestParams: AdsRequestParams
            ): Fragment? {
                when (adsType) {
                    AdsType.AD_MOB_BANNER -> {
                        return null
                    }

                    AdsType.AD_MOB_INTERSTITIAL -> {
                        return null
                    }

                    AdsType.AD_MOB_NATIVE, AdsType.GAM_NATIVE -> {
                        return null
                    }

                    AdsType.GAM_BANNER -> {
                        return null
                    }
                }
            }

            override fun onDiscoverAdsRequest(
                adsType: AdsType,
                adsRequestParams: AdsRequestParams,
                view: BannerAdView
            ) = Unit

            override fun onRewardClick() {
                Toast.makeText(
                    this@MicroCommunityActivity, "on User onReward Click", Toast.LENGTH_SHORT
                ).show()

            }

            override fun onWatchNowAction(deeplink: String) = Unit

            override fun onInitiateSeriesPurchase(
                paymentMethod: PaymentMethod,
                inAppSkuId: String?,
                purchaseUrl: String?,
                seriesId: String?,
                episodeNumber: Int,
                onNotifyPaymentResult: (PaymentStatus) -> Unit
            ) {
                if (PaymentMethod.IN_APP_PURCHASE == paymentMethod) {
                    BillingManager.init(
                        activity = this@MicroCommunityActivity,
                        skuId = inAppSkuId.orEmpty(),
                        seriesId = seriesId,
                        onNotifyPaymentResult
                    )
                } else if (PaymentMethod.SUBSCRIPTION_PLAN == paymentMethod) {
                    Toast.makeText(
                        this@MicroCommunityActivity,
                        "onPaywallActionClicked seriesId :$seriesId \n currentEpisode : $episodeNumber \n deeplink : $purchaseUrl",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(
                        "onPaywallActionClicked",
                        "onPaywallActionClicked seriesId :$seriesId \n currentEpisode : $episodeNumber \n deeplink : $purchaseUrl"
                    )
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(purchaseUrl.orEmpty()))
                    try {
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        DialogUtil.showToast(
                            this@MicroCommunityActivity,
                            getString(R.string.gluedin_common_invalid_url)
                        )
                    }
                } else {
                    Toast.makeText(
                        this@MicroCommunityActivity,
                        "onSelectPaymentMethod seriesId :$seriesId \n paymentUrl : $purchaseUrl \n deeplink : $purchaseUrl",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }


        gluedInConfigurations = GluedInInitializer.Configurations.Builder()
            .setSdkCallback(mGISdkCallback)
            .setSdkInitCallback(mGIInitCallback)
            .setGIAssetCallback(giECommerceCallback)
            .setLogEnabled(true, Log.DEBUG)
            .setHttpLogEnabled(true, 3)
            .setApiAndSecret(AppConstants.API_KEY, AppConstants.SECRET_KEY)
            .setFeedType(GluedInInitializer.Configurations.FeedType.VERTICAL)
            .setPreferredLanguage(AppConstants.APP_LANGUAGE)
            .create()

        gluedInConfigurations?.validateGluedInSDK(this)
    }

    private fun showInternetUI() {
        binding?.progressBar?.isVisible = false
        Toast.makeText(
            this,
            getString(R.string.gluedin_common_network_error),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun initVariable() {
        if (!isNetworkAvailable()) {
            showInternetUI()
            return
        }
        appConfigInteractor = AppConfigInteractor()
        binding?.btnRewards?.isVisible = appConfigInteractor?.isRewardEnable() == true

        // Set Challenge Video Rail
        adapterChallenge = SampleVideoAdapter()
        adapterChallenge?.setListener(this)
        binding?.rvChallenge?.apply {
            layoutManager =
                LinearLayoutManager(
                    this@MicroCommunityActivity,
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
            //  addItemDecoration(itemDecoration)
        }
        binding?.rvChallenge?.adapter = adapterChallenge
        getWidgetDetailWithFeed()
    }

    @SuppressLint("SetTextI18n")
    private fun getWidgetDetailWithFeed() {
        lifecycleScope.launch {
            val interactor = WidgetInteractor()
            interactor.getWidgetDetails(
                assetId.orEmpty(),
                success = { widgetConfig, homeFeed ->
                    binding?.mainLayout?.isVisible = true
                    binding?.progressBar?.isVisible = false
                    widgetConfig?.data?.let {
                        widgetConfigDetails = it
                        binding?.layoutCreator?.isVisible =
                            it.creatorEnabled == true && it.challengeInfo == null
                        binding?.layoutChallengeCreator?.isVisible =
                            it.creatorEnabled == true && it.challengeInfo != null
                        if (it.challengeInfo?.leaderboardEnabled == true) {
                            binding?.btnViewLeaderboard?.isVisible =
                                it.challengeInfo?.videoCount != 0
                        } else {
                            binding?.btnViewLeaderboard?.isVisible = false
                        }

                        challengeEndDate = it.challengeInfo?.endDate
                        expireDays = TimeUtility.calculateDaysUntil(challengeEndDate)
                        binding?.tvChallengeTitle?.text = "#${it.challengeInfo?.title}"
                        binding?.ivChallengeImage?.setImageURI(it.challengeInfo?.image)
                        it.widgetTitle?.english.orEmpty().trim().let { _ ->
                            binding?.tvWidgetTitle?.text = it.widgetTitle?.english
                        }

                        it.creatorTitle?.english.orEmpty().trim().let { _ ->
                            binding?.tvCreatorTitle?.text = it.creatorTitle?.english
                        }

                    }
                    adapterChallenge?.resetDataList(
                        updateList(homeFeed?.listVideo ?: emptyList())
                    )
                    binding?.tvWidgetTitle?.isVisible = homeFeed?.listVideo?.isNotEmpty() == true
                },
                failure = { failedMessage, _ ->
                    if (failedMessage != null) {
                        Toast.makeText(
                            this@MicroCommunityActivity,
                            failedMessage,
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    } else {
                        Toast.makeText(
                            this@MicroCommunityActivity,
                            "Something went wrong! Please retry",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    binding?.progressBar?.isVisible = false
                }
            )

        }
    }

    private fun updateList(list: List<VideoInfo>): List<VideoInfo> {
        val userList = appConfigInteractor?.getBlockUserByUserId() ?: emptyList()
        return list.filterNot { userList.contains(it.userId) }
    }

    override fun onVideoClick(position: Int, selectedVideoId: String) {
        launch(
            GluedInConstants.EntryPoint.SUB_FEED,
            assetsInformation = getProductInfo(),
            selectedVideoId = selectedVideoId,
        )
    }

    private fun launch(
        entryPoint: GluedInConstants.EntryPoint,
        assetsInformation: AssetsInformation? = null,
        selectedVideoId: String? = null,
        isRewardCallback: Boolean? = false
    ) {
        if (!isNetworkAvailable()) {
            Toast.makeText(
                this,
                getString(R.string.gluedin_common_network_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        gluedInConfigurations?.launchSDK(
            this,
            LaunchConfig(
                entryPoint,
                assetsInformation,
                widgetConfigDetails,
                selectedVideoId,
                isRewardCallback
            )
        )
    }

    private fun getProductInfo(): AssetsInformation {
        return AssetsInformation(
            id = AppConstants.assetId,
            assetName = AppConstants.assetName,
            discountPrice = AppConstants.discountPrice.toDouble(),
            imageUrl = AppConstants.imageUri,
            discountEndDate = AppConstants.discountEndDate,
            discountStartDate = AppConstants.discountStartDate,
            callToAction = AppConstants.callToAction,
            mrp = AppConstants.mrp.toDouble(),
            shoppableLink = AppConstants.shoppableLink,
            currencySymbol = AppConstants.currency
        )
    }

    private fun showCreatorAlert() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("This challenge has already been on $challengeEndDate")
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    val giECommerceCallback = object : GIAssetCallback {

        override fun onUserAction(
            context: Context,
            action: UserAction,
            assetId: String?,
            eventRefId: Int?,
            callback: ((Int) -> Unit)?
        ) {
            if (UserAction.ADD_TO_CART == action) {
                ShopifyCartManager.init(this@MicroCommunityActivity)
                callback?.let {
                    ShopifyCartManager.showProductDetails(
                        context, assetId.toString(),
                        it
                    )
                }
            }
        }

        override fun navigateToCart() {
            val intent = Intent(this@MicroCommunityActivity, ViewCartActivity::class.java)
            startActivity(intent)
        }

        override fun getCartItemCount(callback: (Int) -> Unit) {
            ShopifyCartManager.init(this@MicroCommunityActivity)
            ShopifyCartManager.getTotalCartItems() { cartCount ->
                callback(cartCount)
            }
        }

        override fun showOrderHistory() {
            ShopifyCartManager.init(this@MicroCommunityActivity)
            val orderHistory = ShopifyCartManager.getOderHistoryUrl()
            val intent = Intent(this@MicroCommunityActivity, WebViewActivity::class.java)
            intent.putExtra("url", orderHistory)
            intent.putExtra("title", "My Orders")
            startActivity(intent)
        }

    }
}