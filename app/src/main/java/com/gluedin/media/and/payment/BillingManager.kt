package com.gluedin.media.and.payment

import android.app.Activity
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.gluedin.callback.PaymentStatus
import org.json.JSONObject
import timber.log.Timber

object BillingManager {
    private var notifyPaymentResult: ((PaymentStatus) -> Unit)? = null
    private var mPymentSeriesId: String? = null
    private var mSkuId: String? = null

    private var mBillingClient: BillingClient? = null

    fun init(
        activity: Activity,
        skuId: String,
        seriesId: String?,
        callback: (PaymentStatus) -> Unit
    ) {
        mSkuId = skuId
        mPymentSeriesId = seriesId
        notifyPaymentResult = callback

        setupBillingClient(activity)
    }

    private fun setupBillingClient(activity: Activity) {
        mBillingClient = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()

        mBillingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.tag("Billing").d("BillingClient is ready")
                    launchPurchaseFlow(activity)
                }
            }

            override fun onBillingServiceDisconnected() {
                Timber.tag("Billing").d("BillingClient disconnected.")
            }
        })
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Timber.tag("Billing").d("✅ Purchase success by user")
                purchases?.forEach { handlePurchase(it) }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Timber.tag("Billing").d("❌ Purchase canceled by user")
                paymentSDKCallBack(PaymentStatus.PaymentCancelled)
            }

            else -> {
                Timber.tag("Billing").d("❌ Unknown error occurred")
                paymentSDKCallBack(PaymentStatus.PaymentFailed)
            }
        }
    }

    private fun launchPurchaseFlow(
        activity: Activity
    ) {
        checkIfAlreadyPurchased() { alreadyPurchased, purchase ->
            if (alreadyPurchased) {
                val owned =
                    purchase?.purchaseState == Purchase.PurchaseState.PURCHASED && purchase.isAcknowledged
                if (owned) {
                    val orderId = JSONObject(purchase?.originalJson).optString("orderId", "")
                    activity.showToast( "You have already purchased this item")
                    Timber.tag("Billing").d("✅ Already purchased and acknowledged: $orderId")
                    createTransaction(orderId)
                } else {
                    purchase?.let { handlePurchase(it) }
                }
            } else {
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(mSkuId.orEmpty())
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        )
                    )
                    .build()

                mBillingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                        productDetailsList.productDetailsList.isNotEmpty()
                    ) {
                        val productDetails = productDetailsList.productDetailsList[0]
                        val billingFlowParams = BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(
                                listOf(
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails)
                                        .build()
                                )
                            ).build()

                        val billingResult =
                            mBillingClient?.launchBillingFlow(activity, billingFlowParams)
                        if (billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
                            Timber.tag("Billing").d("✅ Payment UI was launched (initiated)")
                            paymentSDKCallBack(PaymentStatus.PaymentStarted)
                        } else {
                            Timber.tag("Billing").d("❌ Failed to load product")
                            paymentSDKCallBack(PaymentStatus.PaymentFailed)
                        }
                    } else {
                        Timber.tag("Billing").d("❌ Failed to load product")
                        paymentSDKCallBack(PaymentStatus.PaymentFailed)
                    }
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        val orderId = JSONObject(purchase.originalJson).optString("orderId", "")
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            mBillingClient?.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.tag("Billing").d("✅ Purchase acknowledged: $orderId")
                    createTransaction(orderId)
                } else {
                    Timber.tag("Billing").d("❌ Acknowledge failed: $orderId")
                    paymentSDKCallBack(PaymentStatus.PaymentFailed)
                }
            }
        }
    }

    private fun checkIfAlreadyPurchased(
        onResult: (Boolean, Purchase?) -> Unit
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        mBillingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val match = purchases.find { it.products.contains(mSkuId.orEmpty()) }
                if (match != null && match.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    onResult(true, match)
                } else {
                    onResult(false, null)
                }
            } else {
                Timber.tag("Billing").d("Query failed: ${billingResult.debugMessage}")
                onResult(false, null)
            }
        }
    }

    private fun createTransaction(transactionId: String) {
        paymentSDKCallBack( PaymentStatus.PaymentSuccess)
    }

    private fun paymentSDKCallBack(status: PaymentStatus) {
        if (status != PaymentStatus.PaymentStarted && status != PaymentStatus.PaymentSuccess) {
            isPaymentSuccessful { success ->
                if (success) {
                    notifyPaymentResult?.invoke(PaymentStatus.PaymentSuccess)
                } else {
                    notifyPaymentResult?.invoke(status)
                }
            }
        } else {
            notifyPaymentResult?.invoke(status)
        }

        if (PaymentStatus.PaymentStarted != status) {
            mBillingClient?.endConnection()
            mBillingClient = null
        }
    }

    fun isPaymentSuccessful(
        callback: (Boolean) -> Unit
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        mBillingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val match = purchases.find { it.products.contains(mSkuId) }
                val success = match != null &&
                        match.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        match.isAcknowledged
                callback(success)
            } else {
                callback(false)
            }
        }
    }

    private fun Activity.showToast(message: String) {
        this.runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}

