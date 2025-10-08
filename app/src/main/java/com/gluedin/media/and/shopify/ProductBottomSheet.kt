package com.gluedin.media.and.shopify

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gluedin.media.and.R
import com.gluedin.media.and.databinding.ShopifyAddToCartBottomSheetBinding
import com.gluedin.presentation.utils.BaseBottomSheet
import com.google.android.material.bottomsheet.BottomSheetBehavior


/**
 * Bottom sheet dialog for displaying product details and adding an item to the Shopify cart.
 *
 * This dialog shows:
 * - Product title, description, image, price, and availability.
 * - A dynamic list of variant options (e.g., color, size).
 * - An "Add to Cart" button that invokes [onAddToCart] with the selected variant.
 *
 * @param product The [Product] object containing all product details.
 * @param onAddToCart A callback invoked when a variant is selected and added to the cart.
 */
class ProductBottomSheetDialog(
    private val product: Product,
    private val onAddToCart: (Product, String) -> Unit
) : BaseBottomSheet() {

    private lateinit var binding: ShopifyAddToCartBottomSheetBinding

    // ----------------------------
    // Lifecycle Methods
    // ----------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the bottom sheet layout
        binding = ShopifyAddToCartBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        // Apply background dim effect when showing the bottom sheet
        dialog?.window?.let { window ->
            window.attributes = window.attributes?.apply {
                dimAmount = 0.60f
                flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        configureBottomSheetHeight()
    }

    // ----------------------------
    // UI Initialization
    // ----------------------------

    /**
     * Configures bottom sheet peek height to 80% of the screen height.
     */
    private fun configureBottomSheetHeight() {
        val bottomSheet =
            dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val displayMetrics = resources.displayMetrics
            behavior.peekHeight = (displayMetrics.heightPixels * 0.8).toInt()
        }
    }

    /**
     * Populates the UI elements with product data and sets up variant options and actions.
     */
    private fun setupUI() {
        binding.apply {
            // 🏷️ Product details
            productTitle.text = product.title
            variantTitle.text = product.variantName.plus(" :")
            productDesc.text = product.description
            productImage.setImageURI(product.imageUrl)

            // 💰 Display price with correct currency
            val currency = ShopifyCartManager.currencySymbol(product.currency)
            productPrice.text = String.format("%s %s", currency, product.price)

            // ⚠️ Handle "Sold Out" state
            if (!product.availableForSale) {
                btnAddToCart.isEnabled = false
                btnAddToCart.alpha = 0.5f
                btnAddToCart.text = "Sold Out"
            } else {
                btnAddToCart.isEnabled = true
                btnAddToCart.alpha = 1f
                btnAddToCart.text = "Add to Cart"
            }

            // 🧩 Build variant options dynamically (RadioButtons)
            optionsGroup.removeAllViews()
            product.variantsOptions.forEachIndexed { index, option ->
                val radioButton = RadioButton(context).apply {
                    text = option
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    id = View.generateViewId()
                    buttonDrawable = ContextCompat.getDrawable(context, R.drawable.custom_radio)
                    setPadding(16, 8, 16, 8)
                }

                // Add spacing between radio buttons (except last one)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index != product.variantsOptions.size - 1) {
                        marginEnd = (16 * resources.displayMetrics.density).toInt()
                    }
                }

                radioButton.layoutParams = params
                optionsGroup.addView(radioButton)

                // Check the first option by default
                if (index == 0) radioButton.isChecked = true
            }

            // 🛒 Add to Cart button click
            btnAddToCart.setOnClickListener {
                val checkedId = optionsGroup.checkedRadioButtonId
                val selectedRadio = root.findViewById<RadioButton>(checkedId)
                val selectedOption = selectedRadio?.text.toString()

                // Find corresponding variant ID
                val selectedIndex = product.variantsOptions.indexOf(selectedOption)
                val selectedVariantId = product.variantsId.getOrNull(selectedIndex)

                // Notify parent and close dialog
                selectedVariantId?.let {
                    onAddToCart(product, it)
                    dismiss()
                } ?: run {
                    Toast.makeText(context, "Invalid variant selection", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

