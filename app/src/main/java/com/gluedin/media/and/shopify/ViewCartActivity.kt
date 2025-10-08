package com.gluedin.media.and.shopify

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gluedin.media.and.R
import com.gluedin.media.and.databinding.ActivityViewCartBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Activity to display and manage the user's shopping cart.
 *
 * Features:
 * - Fetches and displays cart items using [ShopifyCartManager].
 * - Updates subtotal and item count dynamically.
 * - Handles quantity changes and item removal.
 * - Navigates to the Shopify checkout screen via [WebViewActivity].
 */
class ViewCartActivity : AppCompatActivity(), CartAdapter.CartListener {

    private var adapter: CartAdapter? = null
    private var binding: ActivityViewCartBinding? = null
    private var linerLayoutManager: LinearLayoutManager? = null

    // ---------------------------
    // Lifecycle Methods
    // ---------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewCartBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        applySystemBottomMargin()
        initVariable()
    }

    /**
     * Adjusts bottom margin dynamically based on system navigation insets.
     * Prevents the bottom layout from overlapping with gesture bars.
     */
    private fun applySystemBottomMargin() {
        ViewCompat.setOnApplyWindowInsetsListener(binding?.root as View) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = bottomInset
            view.layoutParams = params
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        binding?.progress?.isVisible = true
        binding?.tvTitle?.text = "Your Cart (0)"
        binding?.bottomLayout?.isVisible = false

        // Reset adapter before fetching new data
        adapter?.remove()
        setAdapter()
    }

    // ---------------------------
    // Initialization
    // ---------------------------

    /**
     * Initializes UI variables, event listeners, and system bar appearance.
     */
    private fun initVariable() {
        // Set dark system bars for this screen
        window.navigationBarColor = ContextCompat.getColor(this, R.color.app_black)
        window.statusBarColor = ContextCompat.getColor(this, R.color.app_black)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // ✅ Checkout button click — launch checkout WebView
        binding?.btnCheckout?.setOnClickListener {
            ShopifyCartManager.init(this)
            val cartId = ShopifyCartManager.getCartId()

            ShopifyCartManager.getCheckoutUrl(cartId.toString()) { checkoutUrl ->
                if (checkoutUrl != null) {
                    val intent = Intent(this@ViewCartActivity, WebViewActivity::class.java)
                    intent.putExtra("url", checkoutUrl)
                    intent.putExtra("title", "Checkout")
                    startActivity(intent)
                }
            }
        }

        // 🔙 Back button click
        binding?.ivBack?.setOnClickListener { finish() }
    }

    // ---------------------------
    // RecyclerView Setup
    // ---------------------------

    /**
     * Fetches the latest cart details from Shopify and sets up the RecyclerView adapter.
     */
    private fun setAdapter() {
        ShopifyCartManager.init(this)
        val cartId = ShopifyCartManager.getCartId()

        // Fetch all items currently in the Shopify cart
        ShopifyCartManager.getCartDetails(cartId) { result ->
            result.onSuccess { items ->
                lifecycleScope.launch(Dispatchers.Main) {
                    if (items.isNotEmpty()) {
                        // Hide error and show items
                        binding?.errorMessage?.isVisible = false

                        // Debug print of cart items
                        items.forEach {
                            println("🛒 ${it.variantTitle} x${it.quantity} @ ${it.currencyCode} ${it.price}")
                        }

                        // Setup RecyclerView and adapter
                        adapter = CartAdapter(items as MutableList<CartItem>, this@ViewCartActivity)
                        linerLayoutManager = LinearLayoutManager(this@ViewCartActivity).apply {
                            orientation = LinearLayoutManager.VERTICAL
                        }

                        binding?.recyclerViewCart?.apply {
                            layoutManager = linerLayoutManager
                            adapter = this@ViewCartActivity.adapter
                        }

                        // Update total and show bottom layout
                        updateSubtotal(items.toMutableList())
                        binding?.bottomLayout?.isVisible = true
                    } else {
                        // Empty cart state
                        binding?.errorMessage?.isVisible = true
                    }

                    // Hide loader
                    binding?.progress?.isVisible = false
                }
            }.onFailure { error ->
                // Handle error and show empty view
                binding?.progress?.isVisible = false
                binding?.errorMessage?.isVisible = true
                println("❌ Failed: ${error.message}")
            }
        }
    }

    // ---------------------------
    // Cart Update Logic
    // ---------------------------

    /**
     * Calculates and updates subtotal and cart title (total quantity).
     */
    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun updateSubtotal(cartItems: MutableList<CartItem>) {
        if (cartItems.isNotEmpty()) {
            val subtotal = cartItems.sumOf { it.price * it.quantity }

            // Round to 2 decimal places
            val formattedSubtotal = BigDecimal(subtotal)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString()

            val currency = ShopifyCartManager.currencySymbol(
                cartItems.firstOrNull()?.currencyCode ?: "SGD"
            )

            // Update subtotal text
            binding?.tvProductPrice?.text = "$currency $formattedSubtotal"

            // Update title with total item count
            val totalQuantity = cartItems.sumOf { it.quantity }
            binding?.tvTitle?.text = "Your Cart (${String.format("%02d", totalQuantity)})"
        }
    }

    // ---------------------------
    // CartAdapter Callbacks
    // ---------------------------

    /**
     * Called when an item's quantity changes in the cart.
     */
    override fun onQuantityChanged(item: CartItem) {
        updateSubtotal(adapter?.getItemList() as MutableList<CartItem>)
        binding?.progress?.isVisible = true

        val cartId = ShopifyCartManager.getCartId()

        ShopifyCartManager.updateCartLineQuantity(cartId.toString(), item.lineId, item.quantity) { result ->
            result.onSuccess {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding?.progress?.isVisible = false
                    println("✅ Quantity updated successfully")
                }
            }.onFailure { error ->
                lifecycleScope.launch(Dispatchers.Main) {
                    binding?.progress?.isVisible = false
                    Toast.makeText(
                        this@ViewCartActivity,
                        "Failed to update quantity",
                        Toast.LENGTH_SHORT
                    ).show()
                    println("❌ Failed to update quantity: ${error.message}")
                }
            }
        }
    }

    /**
     * Called when an item is removed from the cart.
     */
    override fun onItemRemoved(item: CartItem) {
        updateSubtotal(adapter?.getItemList() as MutableList<CartItem>)

        if (adapter?.getItemList()?.isEmpty() == true) {
            // Empty cart state
            binding?.tvTitle?.text = "Your Cart (0)"
            binding?.errorMessage?.isVisible = true
            binding?.bottomLayout?.isVisible = false
        } else {
            binding?.errorMessage?.isVisible = false
            binding?.bottomLayout?.isVisible = true
        }

        binding?.progress?.isVisible = true
        val cartId = ShopifyCartManager.getCartId()

        ShopifyCartManager.removeCartLine(cartId.toString(), item.lineId) { result ->
            result.onSuccess {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding?.progress?.isVisible = false
                    Toast.makeText(
                        this@ViewCartActivity,
                        "Item removed successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    println("✅ Item removed successfully")
                }
            }.onFailure { error ->
                lifecycleScope.launch(Dispatchers.Main) {
                    binding?.progress?.isVisible = false
                    Toast.makeText(
                        this@ViewCartActivity,
                        "Failed to remove item",
                        Toast.LENGTH_SHORT
                    ).show()
                    println("❌ Failed to remove item: ${error.message}")
                }
            }
        }
    }
}
