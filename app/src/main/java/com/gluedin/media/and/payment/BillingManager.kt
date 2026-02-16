package com.gluedin.media.and.payment

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.gluedin.callback.BasePlanPrice
import com.gluedin.callback.PaymentMethod
import com.gluedin.callback.PaymentStatus
import com.gluedin.callback.PriceParts
import com.gluedin.callback.SubscriptionDetails
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Currency
import java.util.Locale
import kotlin.collections.associateWith
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.set
import kotlin.let
import kotlin.run
import kotlin.text.isNullOrEmpty
import kotlin.text.orEmpty
import androidx.core.net.toUri

object BillingManager {

    private var notifyPaymentResult: ((PaymentStatus, String, String, PaymentMethod) -> Unit)? =
        null

    private var mPaymentSeriesId: String? = null
    private var mSkuId: String? = null
    private var basePlanId: String? = null
    private var mPackageId: String? = null
    private var mPaymentUrl: String? = null
    private var userId: String? = null

    private var mProductType: String = BillingClient.ProductType.SUBS
    private var mBillingClient: BillingClient? = null
    private var paymentMethod: PaymentMethod? = null

    private var isFinalCallbackSent = false

    /* =============================
       INIT
       ============================= */

    fun init(
        activity: Activity,
        skuId: String,
        basePlanId: String,
        seriesId: String?,
        paymentUrl: String?,
        packageId: String?,
        productType: String,
        userId: String = "",
        paymentMethod: PaymentMethod,
        callback: (PaymentStatus, String, String, PaymentMethod) -> Unit
    ) {
        isFinalCallbackSent = false
        this.mSkuId = skuId
        this.basePlanId = basePlanId
        this.mPaymentSeriesId = seriesId
        this.mPaymentUrl = paymentUrl
        this.mPackageId = packageId
        this.notifyPaymentResult = callback
        this.mProductType = productType
        this.userId = userId
        this.paymentMethod = paymentMethod

        setupBillingClient(activity)
    }

    /* =============================
       BILLING CLIENT
       ============================= */

    private fun setupBillingClient(activity: Activity) {
        val weakActivity = WeakReference(activity)

        mBillingClient = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdatedListener(weakActivity))
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        mBillingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(br: BillingResult) {
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    weakActivity.get()?.let { checkExistingSubscriptionAndLaunch(it) }
                } else {
                    paymentCallback(PaymentStatus.PaymentFailed, "")
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun purchasesUpdatedListener(activityRef: WeakReference<Activity>) =
        PurchasesUpdatedListener { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK ->
                    purchases?.forEach { handlePurchase(it) }

                BillingClient.BillingResponseCode.USER_CANCELED ->
                    paymentCallback(PaymentStatus.PaymentCancelled, "")

                else ->
                    paymentCallback(PaymentStatus.PaymentFailed, "")
            }
        }

    /* =============================
       PURCHASE / UPGRADE FLOW
       ============================= */

    private fun checkExistingSubscriptionAndLaunch(activity: Activity) {
        if (mProductType == BillingClient.ProductType.INAPP) {
            launchPurchaseFlow(activity)
            return
        } else {
            launchSubscriptionPurchaseOrUpgrade(activity)
        }
    }

    private fun launchPurchaseFlow(
        activity: Activity,
    ) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(mSkuId.orEmpty())
                .setProductType(mProductType)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        mBillingClient?.queryProductDetailsAsync(params) { br, result ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK ||
                result.productDetailsList.isEmpty()
            ) {
                paymentCallback(PaymentStatus.PaymentFailed, "")
                return@queryProductDetailsAsync
            }

            val productDetails = result.productDetailsList.first()

            val offer = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?: run {
                    paymentCallback(PaymentStatus.PaymentFailed, "")
                    return@queryProductDetailsAsync
                }

            val productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offer.offerToken)
                    .build()

            val flowBuilder =
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .setObfuscatedAccountId(userId.toString())

            val resultFlow =
                mBillingClient?.launchBillingFlow(activity, flowBuilder.build())

            if (resultFlow?.responseCode != BillingClient.BillingResponseCode.OK) {
                paymentCallback(PaymentStatus.PaymentFailed, "")
            }
        }
    }

    /* =============================
       HANDLE PURCHASE
       ============================= */

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val orderId = JSONObject(purchase.originalJson).optString("orderId", "")

        if (purchase.isAcknowledged) {
            paymentCallback(PaymentStatus.PaymentSuccess, orderId)
            return
        }

        val ackParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        mBillingClient?.acknowledgePurchase(ackParams) { br ->
            if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                paymentCallback(PaymentStatus.PaymentSuccess, orderId)
            } else {
                paymentCallback(PaymentStatus.PaymentFailed, "")
            }
        }
    }

    /* =============================
       CALLBACK CLEANUP
       ============================= */

    private fun paymentCallback(status: PaymentStatus, orderId: String) {
        if (isFinalCallbackSent &&
            status != PaymentStatus.PaymentStarted
        ) {
            return
        }
        if (status != PaymentStatus.PaymentStarted) {
            isFinalCallbackSent = true
            //   mBillingClient?.endConnection()
            // mBillingClient = null
        }
        notifyPaymentResult?.invoke(
            status,
            mPackageId.orEmpty(),
            orderId,
            paymentMethod ?: PaymentMethod.IN_APP_PURCHASE
        )


    }

    /* =============================
       MULTI-SKU PRICE FETCH
       ============================= */

    fun fetchPricePartsForSkus(
        activity: Activity,
        skuIds: List<String>,
        productType: String,
        result: (Map<String, Any?>) -> Unit
    ) {
        val validSkus = skuIds
            .filter { it.isNotBlank() }
            .distinct()

        if (validSkus.isEmpty()) {
            result(emptyMap())
            return
        }

        if (mBillingClient == null) {
            mBillingClient = BillingClient.newBuilder(activity)
                .setListener { _, _ -> }
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )
                .build()
        }

        if (mBillingClient!!.isReady) {
            queryPriceParts(validSkus, productType, result)
            return
        }

        mBillingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(br: BillingResult) {
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPriceParts(validSkus, productType, result)
                } else {
                    result(validSkus.associateWith { null })
                }
            }

            override fun onBillingServiceDisconnected() {
                result(validSkus.associateWith { null })
            }
        })
    }


    private fun queryPriceParts(
        skus: List<String>,
        productType: String,
        result: (Map<String, Any?>) -> Unit
    ) {
        val products = skus.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(productType)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        mBillingClient?.queryProductDetailsAsync(params) { billingResult, response ->

            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                result(skus.associateWith { null })
                return@queryProductDetailsAsync
            }

            val returnedMap = response.productDetailsList
                .associateBy { it.productId }

            val finalMap = skus.associateWith { sku ->
                returnedMap[sku]?.let { pd ->
                    when (pd.productType) {
                        BillingClient.ProductType.INAPP ->
                            extractInAppPrice(pd)

                        BillingClient.ProductType.SUBS ->
                            extractSubscriptionDetails(pd)

                        else -> null
                    }
                }
            }

            result(finalMap)
        }
    }

    private fun extractSubscriptionDetails(
        pd: ProductDetails
    ): SubscriptionDetails {

        val basePlans = pd.subscriptionOfferDetails
            ?.flatMap { offer ->

                offer.pricingPhases.pricingPhaseList.mapNotNull { phase ->
                    val amountMicros = phase.priceAmountMicros
                    val period = phase.billingPeriod
                    val days = billingPeriodToDays(period)
                    BasePlanPrice(
                        basePlanId = offer.basePlanId,
                        offerId = offer.offerId,
                        amount = amountMicros / 1_000_000.0,
                        currencyCode = phase.priceCurrencyCode,
                        currencySymbol = safeCurrencySymbol(phase.priceCurrencyCode),
                        days = days,
                    )
                }
            }
            ?: emptyList()

        return SubscriptionDetails(
            productId = pd.productId,
            title = pd.title,
            description = pd.description,
            basePlans = basePlans
        )
    }


    private fun extractInAppPrice(pd: ProductDetails): PriceParts? {
        val offer = pd.oneTimePurchaseOfferDetails ?: return null

        return PriceParts(
            amount = offer.priceAmountMicros / 1_000_000.0,
            currencySymbol = safeCurrencySymbol(offer.priceCurrencyCode),
            currencyCode = offer.priceCurrencyCode,
            productId = pd.productId
        )
    }

    private fun billingPeriodToDays(period: String?): Int? {
        if (period.isNullOrEmpty()) return null
        return when {
            period.endsWith("D") -> period.removePrefix("P").removeSuffix("D").toInt()
            period.endsWith("W") -> period.removePrefix("P").removeSuffix("W").toInt() * 7
            period.endsWith("M") -> period.removePrefix("P").removeSuffix("M").toInt() * 30
            period.endsWith("Y") -> period.removePrefix("P").removeSuffix("Y").toInt() * 365
            else -> null
        }
    }

    private fun safeCurrencySymbol(code: String): String =
        try {
            Currency.getInstance(code).getSymbol(Locale.getDefault())
        } catch (e: Exception) {
            code
        }


    // ───────────────── Launch Subscription Upgrade ─────────────────

    fun launchSubscriptionPurchaseOrUpgrade(
        activity: Activity
    ) {
        getActiveSubscriptionPurchase { oldPurchase ->

            getProductDetails(mSkuId.toString()) { productDetails ->
                if (productDetails == null) return@getProductDetails

                val offerToken = productDetails.subscriptionOfferDetails
                    ?.firstOrNull { it.basePlanId == basePlanId }
                    ?.offerToken
                    ?: return@getProductDetails

                val productDetailsParams =
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()

                val billingParamsBuilder =
                    BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(productDetailsParams))
                        .setObfuscatedAccountId(userId.orEmpty()) // ✅ HERE

                // ✅ Only for upgrade / downgrade
                if (oldPurchase != null) {
                    val updateParams =
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(oldPurchase.purchaseToken)
                            .setSubscriptionReplacementMode(
                                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode
                                    .WITHOUT_PRORATION
                            )
                            .build()

                    billingParamsBuilder.setSubscriptionUpdateParams(updateParams)
                }

                mBillingClient?.launchBillingFlow(
                    activity,
                    billingParamsBuilder.build()
                )
            }
        }
    }


    // ───────────────── Active Subscription ─────────────────

    fun getActiveSubscriptionPurchase(
        onResult: (Purchase?) -> Unit
    ) {
        mBillingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchasesList ->

            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onResult(null)
                return@queryPurchasesAsync
            }

            val activePurchase = purchasesList.firstOrNull { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            onResult(activePurchase)
        }
    }

    // ───────────────── Product Details (v8 async) ─────────────────
    private val cachedProductDetails = mutableMapOf<String, ProductDetails>()

    fun getProductDetails(
        productId: String,
        onResult: (ProductDetails?) -> Unit
    ) {
        cachedProductDetails[productId]?.let {
            onResult(it)
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        mBillingClient?.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onResult(null)
                return@queryProductDetailsAsync
            }

            val details = productDetailsList.productDetailsList.firstOrNull()
            details?.let { cachedProductDetails[productId] = it }

            onResult(details)
        }
    }

    fun getActiveBasePlanId(
        subscriptionProductId: String,
        onResult: (String?) -> Unit
    ) {
        mBillingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { br, purchases ->

            val activePurchase = purchases.firstOrNull {
                it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        it.products.contains(subscriptionProductId)
            }

            val basePlanId = activePurchase?.let {
                JSONObject(it.originalJson).optString("basePlanId", null)
            }

            onResult(basePlanId)
        }
    }

    fun openPlayStoreSubscription(
        context: Context,
        inAppSkuId: String,
        packageName: String
    ) {
        val subscriptionUrl =
            "https://play.google.com/store/account/subscriptions?sku=$inAppSkuId&package=$packageName"

        val fallbackUrl =
            "https://play.google.com/store/account/subscriptions"

        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, subscriptionUrl.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // Fallback to browser
            context.startActivity(
                Intent(Intent.ACTION_VIEW, fallbackUrl.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }


}

