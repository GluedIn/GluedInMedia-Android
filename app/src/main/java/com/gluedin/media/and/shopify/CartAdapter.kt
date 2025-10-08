package com.gluedin.media.and.shopify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gluedin.media.and.databinding.ItemCartBinding

/**
 * RecyclerView Adapter for displaying items in the user's Shopify cart.
 *
 * Handles:
 * - Displaying product details (name, variant, image, price, quantity).
 * - Handling quantity increment/decrement actions.
 * - Handling item removal from the cart.
 *
 * Communicates user actions (quantity change, item removal) via [CartListener].
 *
 * @param items The list of [CartItem] objects in the cart.
 * @param listener Callback interface for cart item actions.
 */
class CartAdapter(
    private val items: MutableList<CartItem>,
    private val listener: CartListener
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    /**
     * Listener interface to communicate cart item events back to the Activity/Fragment.
     */
    interface CartListener {
        fun onQuantityChanged(item: CartItem)
        fun onItemRemoved(item: CartItem)
    }

    /**
     * ViewHolder class representing each cart item view.
     */
    inner class CartViewHolder(private val binding: ItemCartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds the data from a [CartItem] object to the view.
         */
        fun bind(item: CartItem) = with(binding) {
            // 🖼️ Load product image (you may want to use Glide or Coil for better caching)
            productImage.setImageURI(item.imageUrl)

            // 🏷️ Set product and variant details
            tvProductName.text = item.title
            variantTitle.text = item.variantTitle.plus(" :")
            variantName.text = item.variantName

            // 💰 Display product price with currency symbol
            val currency = ShopifyCartManager.currencySymbol(item.currencyCode)
            tvProductPrice.text = String.format("%s %s", currency, item.price)
            tvQuantity.text = item.quantity.toString()

            // ➕ Increase quantity
            btnPlus.setOnClickListener {
                item.quantity++
                notifyItemChanged(bindingAdapterPosition)

                // Notify listener with updated item data
                listener.onQuantityChanged(item.copy(quantity = item.quantity))
            }

            // ➖ Decrease quantity
            btnMinus.setOnClickListener {
                if (item.quantity > 1) {
                    item.quantity--
                    notifyItemChanged(bindingAdapterPosition)

                    listener.onQuantityChanged(item.copy(quantity = item.quantity))
                }
            }

            // 🗑️ Delete item from cart
            ivDelete.setOnClickListener {
                val removedItem = items[bindingAdapterPosition]
                items.removeAt(bindingAdapterPosition)
                notifyItemRemoved(bindingAdapterPosition)
                listener.onItemRemoved(removedItem)
            }
        }
    }

    // ------------------------------
    // RecyclerView Adapter Overrides
    // ------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    // ------------------------------
    // Helper Methods
    // ------------------------------

    /**
     * Returns the current list of cart items.
     */
    fun getItemList(): MutableList<CartItem> = items

    /**
     * Clears all items from the adapter and refreshes the view.
     */
    fun remove() {
        items.clear()
        notifyDataSetChanged()
    }
}

