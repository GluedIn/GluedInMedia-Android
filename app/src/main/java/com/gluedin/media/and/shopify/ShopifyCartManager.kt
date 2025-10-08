package com.gluedin.media.and.shopify

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.shopify.buy3.GraphCallResult
import com.shopify.buy3.GraphClient
import com.shopify.buy3.Storefront
import com.shopify.graphql.support.ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


@SuppressLint("StaticFieldLeak")
object ShopifyCartManager {
    private var graphClient: GraphClient? = null
    private const val TAG = "ShopifyCartManager"
    private const val SHARED_PREFERENCES = "shopify_prefs"//SharedPreferences
    private const val CART_ID = "cart_id"
    private const val CUSTOMER_ACCESS_TOKEN = "customer_access_token"
    private var context: AppCompatActivity? = null
    private var mContext: Context? = null

    /*
    *
    * User Details
    * */
    private val customerEmailId = "user_email_id_for_shopify"
    private val customerPassword = "user_password_for_shopify"
    private val customerFirstName = "user_first_name_for_shopify"
    private val customerLastName = "user_last_name_for_shopify"
    private val address1 = "address_1"
    private val address2 = "address_2"
    private val city = "city_name"
    private val province = "province_name"
    private val zip = "zip_code"
    private val country = "country_name"
    private val phone = "phone_number"

    private val shopDomain = "business_name.myshopify.com/" // Dev
    private val accessToken = "put_your_storefront_access_token_here"


    /**
     * Initializes the Shopify GraphQL client and verifies the customer login state.
     *
     * This method should be called once during app startup (e.g., in Application or MainActivity)
     * to initialize the Shopify [GraphClient] and ensure the user is authenticated.
     *
     * - If a valid customer access token is found, it validates the token with Shopify.
     * - If no token is found or validation fails, it attempts to log in or register the customer.
     *
     * @param applicationContext The [AppCompatActivity] context used to initialize Shopify.
     */
    fun init(applicationContext: AppCompatActivity) {
        this.context = applicationContext

        // ✅ Initialize GraphClient only once
        if (graphClient == null) {
            graphClient = GraphClient.build(
                context = applicationContext,
                shopDomain = shopDomain,
                accessToken = accessToken // Storefront API token
            )
            Timber.d("$TAG -> ✅ GraphClient initialized for $shopDomain")
            checkLoginStatus()
        } else {
            Timber.d("$TAG -> ⚙️ GraphClient already initialized")
            // Re-check login status if no access token exists
            if (getCustomerAccessToken().isNullOrEmpty()) {
                checkLoginStatus()
            }
        }
    }

    /**
     * Ensures that the customer is logged in and has a valid access token.
     *
     * The logic follows this order:
     * 1. If a saved token exists → validate it via Shopify API.
     * 2. If validation fails → attempt login using saved credentials.
     * 3. If no token exists → register a new customer, then log in and add address.
     */
    private fun checkLoginStatus() {
        val token = getCustomerAccessToken()

        // ✅ Case 1: Access token exists → validate it
        if (!token.isNullOrEmpty()) {
            Timber.d("$TAG -> 🔍 Validating existing customer access token...")

            validateCustomerAccessToken { result ->
                result.onSuccess { isValid ->
                    if (isValid) {
                        Timber.d("$TAG -> ✅ Access token is valid")
                    } else {
                        Timber.w("$TAG -> ⚠️ Invalid access token, logging in again...")
                        loginCustomer { loginResult ->
                            loginResult.onSuccess {
                                Timber.d("$TAG -> ✅ Customer logged in successfully")
                            }
                            loginResult.onFailure { error ->
                                Timber.e("$TAG -> ❌ Login failed: ${error.message}")
                            }
                        }
                    }
                }

                result.onFailure { error ->
                    Timber.e("$TAG -> ❌ Token validation failed: ${error.message}")
                }
            }

        } else {
            // 🟠 Case 2: No token found → register a new customer
            Timber.d("$TAG -> 🧑‍💻 No customer found. Registering new account...")

            registerCustomer { registerResult ->
                registerResult.onSuccess {
                    Timber.d("$TAG -> ✅ Registration successful, logging in...")
                    loginCustomer { loginResult ->
                        loginResult.onSuccess {
                            Timber.d("$TAG -> ✅ Login successful, adding default address...")

                            addCustomerAddress { addressResult ->
                                addressResult.onSuccess {
                                    Timber.d("$TAG -> 🏠 Address added successfully")
                                }
                                addressResult.onFailure { error ->
                                    Timber.e("$TAG -> ❌ Failed to add address: ${error.message}")
                                }
                            }
                        }

                        loginResult.onFailure { error ->
                            Timber.e("$TAG -> ❌ Login failed after registration: ${error.message}")
                        }
                    }
                }

                registerResult.onFailure { error ->
                    Timber.e("$TAG -> ❌ Customer registration failed: ${error.message}")
                }
            }
        }
    }



    /**
     * Fetches product details from Shopify and displays them in a bottom sheet dialog.
     *
     * This function performs a GraphQL query using the given product ID to retrieve
     * the product's details such as title, description, price, images, variants, and
     * availability status. Once fetched, it opens a bottom sheet (`ProductBottomSheetDialog`)
     * showing these details. Users can then select a variant and add it to their cart.
     *
     * @param context The current [Context], used to open the bottom sheet.
     * @param productId The Shopify product ID (GraphQL global ID format).
     * @param callback A callback invoked with the updated cart item count
     *                 once the user adds the product to the cart.
     */
    fun showProductDetails(
        context: Context,
        productId: String,
        callback: (Int) -> Unit,
    ) {
        this.mContext = context

        // 🛑 Validate product ID before proceeding
        if (productId.isBlank()) {
            showToast("⚠️ Invalid product ID")
            Timber.e("$TAG -> ❌ showProductDetails called with blank productId")
            return
        }

        // ✅ Convert product ID to Shopify's GraphQL ID type
        val productIdObj = ID(productId)

        // ✅ Build the GraphQL query to fetch product details
        val query = Storefront.query { root ->
            root.node(productIdObj) { node ->
                node.onProduct { product ->
                    product
                        .title()
                        .description()
                        .images({ args -> args.first(1) }) { imgConnection ->
                            imgConnection.edges { edge ->
                                edge.node { image ->
                                    image.url()
                                }
                            }
                        }
                        .options { option ->
                            option.name()
                        }
                        .variants({ args -> args.first(10) }) { variantConnection ->
                            variantConnection.edges { edge ->
                                edge.node { variant ->
                                    variant.title()
                                    variant.availableForSale()   // 👈 Can the item be purchased
                                    variant.quantityAvailable()  // 👈 How many are left
                                    variant.price { price ->
                                        price.amount()
                                        price.currencyCode()
                                    }
                                    variant.selectedOptions { selected ->
                                        selected.name()   // e.g. "Size"
                                        selected.value()  // e.g. "Medium"
                                    }
                                }
                            }
                        }
                }
            }
        }

        // ✅ Execute the product details query
        graphClient?.queryGraph(query)?.enqueue { result ->
            when (result) {

                // 🟢 Successfully fetched product data
                is GraphCallResult.Success -> {
                    try {
                        val node = result.response.data?.node as? Storefront.Product
                        if (node == null) {
                            showToast("⚠️ Product not found")
                            Timber.w("$TAG -> ❌ No product found for ID: $productId")
                            return@enqueue
                        }

                        val firstVariant = node.variants.edges.firstOrNull()?.node
                        val availableForSale = firstVariant?.availableForSale ?: false

                        // ✅ Map Shopify response to local Product model
                        val product = Product(
                            id = node.id.toString(),
                            title = node.title.orEmpty(),
                            description = node.description.orEmpty(),
                            price = firstVariant?.price?.amount.toString(),
                            currency = firstVariant?.price?.currencyCode.toString(),
                            imageUrl = node.images.edges.firstOrNull()?.node?.url.orEmpty(),
                            variantsOptions = node.variants.edges.map { it.node.title },
                            variantsId = node.variants.edges.map { it.node.id.toString() },
                            variantName = node.options.firstOrNull()?.name.orEmpty(),
                            availableForSale = availableForSale
                        )

                        // ✅ Show product in bottom sheet dialog
                        openProductDetails(context, product, callback)

                        Timber.tag(TAG).d("🛍️ Product fetched successfully: $product")

                    } catch (e: Exception) {
                        Timber.e(e, "$TAG -> ⚠️ Failed to parse product response")
                        showToast("Something went wrong while loading the product.")
                    }
                }

                // 🔴 Network or API failure
                is GraphCallResult.Failure -> {
                    Timber.e("$TAG -> ❌ Failed to fetch product: ${result.error.message}")
                    showToast("⚠️ Failed to load product details. Please try again.")
                }
            }
        }
    }


    /**
     * Retrieves the saved Shopify cart ID from [SharedPreferences].
     *
     * This function is used to access the locally stored cart ID that was
     * previously created via `createCartAndAddCartItem()`. The cart ID allows
     * Shopify to identify and persist the user’s active cart between app sessions.
     *
     * @return The saved cart ID as a [String], or `null` if no cart has been created.
     */

    fun getCartId(): String? {
        // Access the shared preferences where the cart ID is stored
        val prefs = context?.getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE)

        // Retrieve the saved cart ID, or return null if not found
        return prefs?.getString(CART_ID, null)
    }



    /**
     * Retrieves the Shopify checkout URL for the given cart ID.
     *
     * This function updates the cart’s buyer identity with the current
     * customer’s access token (if available) and then retrieves the checkout
     * URL associated with that cart. The returned URL can be opened in a
     * WebView or external browser to complete the checkout process.
     *
     * @param cartId The unique ID of the Shopify cart.
     * @param callback A callback invoked with the checkout URL (`String?`).
     *                 - Returns a valid URL if successful.
     *                 - Returns `null` if the operation fails.
     */
    fun getCheckoutUrl(
        cartId: String,
        callback: (String?) -> Unit
    ) {
        // 🛑 Validate input before proceeding
        if (cartId.isBlank()) {
            Timber.e("$TAG -> ❌ Invalid cartId provided")
            callback(null)
            return
        }

        val cartIdObj = ID(cartId)

        // ✅ Build buyer identity input using the stored customer access token (if available)
        val buyerIdentityInput = Storefront.CartBuyerIdentityInput()
            .setCustomerAccessToken(getCustomerAccessToken())

        // ✅ Define the GraphQL mutation to update buyer identity and fetch checkout URL
        val mutation = Storefront.mutation { root ->
            root.cartBuyerIdentityUpdate(cartIdObj, buyerIdentityInput) { update ->
                update.cart { cart ->
                    cart.checkoutUrl() // Fetch checkout URL after identity update

                    // Optionally fetch associated customer details
                    cart.buyerIdentity { identity ->
                        identity.customer { customer ->
                            customer.email()
                            customer.firstName()
                            customer.lastName()
                        }
                    }
                }

                // Capture potential API validation errors
                update.userErrors { err ->
                    err.field()
                    err.message()
                }
            }
        }

        // ✅ Execute the mutation asynchronously via Shopify GraphClient
        graphClient?.mutateGraph(mutation)?.enqueue { result ->
            when (result) {

                // 🟢 Mutation succeeded
                is GraphCallResult.Success -> {
                    val checkoutUrl = result.response.data
                        ?.cartBuyerIdentityUpdate
                        ?.cart
                        ?.checkoutUrl

                    Timber.d("$TAG -> 🛒 Checkout URL: $checkoutUrl")

                    if (context != null && Looper.myLooper() == Looper.getMainLooper()) {
                        context?.lifecycleScope?.launch(Dispatchers.Main) {
                            callback(checkoutUrl)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            callback(checkoutUrl)
                        }
                    }
                }

                // 🔴 Mutation failed — log and return null
                is GraphCallResult.Failure -> {
                    Timber.e("$TAG -> ❌ Failed to retrieve checkout URL: ${result.error.message}")
                    if (context != null && Looper.myLooper() == Looper.getMainLooper()) {
                        context?.lifecycleScope?.launch(Dispatchers.Main) {
                            callback(null)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            callback(null)
                        }
                    }
                }
            }
        }
    }


    /**
     * Displays a bottom sheet dialog showing the product details.
     *
     * When the user selects a variant or option from the bottom sheet,
     * this function automatically ensures that there is an active cart
     * and adds the selected product variant to it.
     *
     * @param activity The activity context used to display the bottom sheet.
     * @param mProduct The product data to display in the bottom sheet.
     * @param callback A callback invoked with the updated cart item count
     *                 after a variant is added to the cart.
     */
    private fun openProductDetails(
        activity: Context,
        mProduct: Product,
        callback: (Int) -> Unit
    ) {
        // 🛑 Safety check — ensure product is valid
        if (mProduct.id == "null" || mProduct.id.isBlank()) {
            showToast("⚠️ Product not found")
            return
        }

        // ✅ Initialize bottom sheet dialog with product details
        val productBottomSheetDialog = ProductBottomSheetDialog(
            product = mProduct
        ) { product, option ->
            // When user adds item from bottom sheet → ensure cart and add product
            ensureActiveCartAndAddItem(option, callback)
        }

        // ✅ Safely get the FragmentManager from the provided context
        val fragmentManager = getFragmentManager(activity)

        // ✅ Show the bottom sheet if FragmentManager is available
        fragmentManager?.let {
            productBottomSheetDialog.show(it, TAG)
            Timber.d("$TAG -> 🛍️ Opened product details for ${mProduct.title}")
        } ?: run {
            Timber.e("$TAG -> ❌ Failed to open product details: FragmentManager is null")
            showToast("⚠️ Unable to display product details")
        }
    }


    /**
     * Retrieves a [FragmentManager] instance from the given [Context].
     *
     * This utility safely checks whether the provided context is associated with
     * an activity that supports fragments (e.g., [AppCompatActivity] or [FragmentActivity]).
     *
     * @param context The context from which to extract the [FragmentManager].
     * @return The [FragmentManager] if available, or `null` if the context is not a fragment-capable activity.
     */
    fun getFragmentManager(context: Context): FragmentManager? {
        return when (context) {
            // ✅ Most common case — AppCompatActivity
            is AppCompatActivity -> context.supportFragmentManager

            // 🟢 Covers FragmentActivity subclasses that may not be AppCompatActivity
            is FragmentActivity -> context.supportFragmentManager

            // 🔴 Context is not an activity — no FragmentManager available
            else -> null
        }
    }



    /**
     * Ensures there is an active Shopify cart before adding a product variant to it.
     *
     * This function performs the following logic:
     * 1. Checks if a cart ID exists locally (saved from previous sessions).
     * 2. If no cart ID is found, it creates a new cart and adds the product.
     * 3. If a cart ID exists, it queries Shopify to ensure the cart is still valid.
     * 4. If the existing cart is invalid (e.g., completed checkout), a new cart is created.
     * 5. Otherwise, the item is simply added to the existing active cart.
     *
     * @param option The product variant ID to add to the cart.
     * @param callback A callback invoked with the updated total cart item count.
     */
    fun ensureActiveCartAndAddItem(
        option: String,
        callback: (Int) -> Unit
    ) {
        // ✅ Retrieve locally stored cart ID (if available)
        val cartId = getCartId()

        // 🟠 No cart found — create one and add the item immediately
        if (cartId == null) {
            Timber.d("$TAG -> 🆕 No existing cart found. Creating a new one.")
            createCartAndAddCartItem(option, callback)
            return
        }

        // ✅ Verify if the existing cart is still active on Shopify
        val query = Storefront.query { root ->
            root.cart(ID(cartId)) { cart ->
                cart.checkoutUrl() // Minimal query to verify cart existence
            }
        }

        // Execute query to check cart status
        graphClient?.queryGraph(query)?.enqueue { result ->
            when (result) {

                // 🟢 Successfully queried cart
                is GraphCallResult.Success -> {
                    val cart = result.response.data?.cart
                    if (cart == null) {
                        // 🛑 Cart does not exist anymore (e.g., checked out or deleted)
                        Timber.w("$TAG -> ⚠️ Cart is no longer active. Creating new cart...")
                        createCartAndAddCartItem(option, callback)
                    } else {
                        // 🟢 Cart is valid — proceed to add item
                        Timber.d("$TAG -> ✅ Active cart found. Adding item...")
                        addItemToCart(cartId, option, callback)
                    }
                }

                // 🔴 GraphQL/network failure — fallback to creating a new cart
                is GraphCallResult.Failure -> {
                    Timber.e("$TAG -> ❌ Failed to verify cart: ${result.error.message}")
                    createCartAndAddCartItem(option, callback)
                }
            }
        }
    }


    /**
     * Creates a new Shopify cart and immediately adds a product variant to it.
     *
     * This function performs a two-step operation:
     * 1. Creates a new empty cart using Shopify’s Storefront API.
     * 2. Adds a specified product variant (line item) to the newly created cart.
     *
     * The newly created cart ID is also stored locally in SharedPreferences
     * for subsequent cart operations.
     *
     * @param option The product variant ID to add to the cart (Shopify variant ID).
     * @param callback A callback returning the updated total cart item count after the item is added.
     */
    fun createCartAndAddCartItem(
        option: String,
        callback: (Int) -> Unit
    ) {
        // ✅ Define the GraphQL mutation to create a new cart
        val mutation = Storefront.mutation { root ->
            root.cartCreate({ }) { payload ->
                // Request basic cart fields
                payload.cart { cart ->
                    cart.totalQuantity()
                }

                // Capture any Shopify user validation errors
                payload.userErrors { error ->
                    error.field()
                    error.message()
                }
            }
        }

        // ✅ Execute the mutation asynchronously
        graphClient?.mutateGraph(mutation)?.enqueue { result ->
            when (result) {

                // 🟢 Successfully created a cart
                is GraphCallResult.Success -> {
                    val cart = result.response.data?.cartCreate?.cart

                    if (cart != null) {
                        val cartId = cart.id.toString()

                        // Save the new cart ID locally for future use
                        val prefs = context?.getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE)
                        prefs?.edit()?.putString(CART_ID, cartId)?.apply()

                        // Log creation success
                        Timber.d("$TAG -> ✅ Cart created. ID: $cartId | Items: ${cart.totalQuantity}")

                        // Immediately add selected item (variant) to the new cart
                        addItemToCart(cartId, option, callback)
                    } else {
                        Timber.e("$TAG -> ⚠️ Cart creation returned null cart object")
                        callback.invoke(0)
                    }
                }

                // 🔴 Cart creation failed (network or GraphQL error)
                is GraphCallResult.Failure -> {
                    Timber.e("$TAG -> ❌ Failed to create cart: ${result.error.message}")
                    if (context != null && Looper.myLooper() == Looper.getMainLooper()) {
                        context?.lifecycleScope?.launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "⚠️ Failed to create cart. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    callback.invoke(0)
                }
            }
        }
    }


    /**
     * Adds a product variant to the user's Shopify cart.
     *
     * This function performs a GraphQL mutation to add a new line (product variant)
     * to the specified cart on Shopify. If successful, it returns the updated total
     * item count in the cart and displays a confirmation message to the user.
     *
     * @param cartId The unique ID of the Shopify cart.
     * @param mVariantId The ID of the product variant to add.
     * @param callback A callback returning the updated total cart item count.
     */
    fun addItemToCart(
        cartId: String,
        mVariantId: String,
        callback: (Int) -> Unit
    ) {
        // 🛑 Validate inputs before proceeding
        if (cartId.isBlank() || mVariantId.isBlank()) {
            Timber.e("$TAG -> ❌ Invalid cartId or variantId")
            callback.invoke(0)
            return
        }

        // ✅ Create the input for the cart line (variant + quantity)
        val variantId = ID(mVariantId)
        val lineInput = Storefront.CartLineInput(variantId).setQuantity(1)

        // ✅ Define the GraphQL mutation to add the line to the cart
        val mutation = Storefront.mutation { root ->
            root.cartLinesAdd(ID(cartId), listOf(lineInput)) { payload ->
                // Request updated total quantity from the cart
                payload.cart { cart ->
                    cart.totalQuantity()
                }

                // Capture any user validation or Shopify errors
                payload.userErrors { error ->
                    error.field()
                    error.message()
                }
            }
        }

        // ✅ Execute the mutation asynchronously using Shopify's GraphClient
        graphClient?.mutateGraph(mutation)?.enqueue { result ->
            when (result) {

                // 🟢 Successfully added item to cart
                is GraphCallResult.Success -> {
                    val cart = result.response.data?.cartLinesAdd?.cart
                    val newCount = cart?.totalQuantity ?: 0

                    // Safely update UI (Toast + callback) on the main thread
                    if (context != null && Looper.myLooper() == Looper.getMainLooper()) {
                        context?.lifecycleScope?.launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "🎉 Great choice! Your product is now in the cart.",
                                Toast.LENGTH_SHORT
                            ).show()
                            callback.invoke(newCount)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                mContext,
                                "🎉 Great choice! Your product is now in the cart.",
                                Toast.LENGTH_SHORT
                            ).show()
                            callback.invoke(newCount)
                        }
                    }

                    Timber.d("$TAG -> ✅ Item added to cart successfully (new count: $newCount)")
                }

                // 🔴 Network or GraphQL failure
                is GraphCallResult.Failure -> {
                    Timber.e("$TAG -> ❌ Failed to add item: ${result.error.message}")
                    if (context != null && Looper.myLooper() == Looper.getMainLooper()) {
                        context?.lifecycleScope?.launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "⚠️ Failed to add item to cart. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    callback.invoke(0)
                }
            }
        }
    }


    /**
     * Generates the Shopify "Order History" (Customer Account) URL.
     *
     * If a valid customer access token exists, it appends the token to the
     * account URL as a query parameter, enabling direct access to the user's
     * order history without requiring an additional login.
     *
     * @return A fully qualified Shopify account URL that includes the
     *         `customerAccessToken` if the user is logged in.
     */
    fun getOderHistoryUrl(): String {
        // Retrieve the current customer access token once for efficiency
        val accessToken = getCustomerAccessToken()

        return if (!accessToken.isNullOrBlank()) {
            // ✅ Logged-in customer: include token for direct access
            "https://$shopDomain/account?customerAccessToken=$accessToken"
        } else {
            // 🔹 Guest or logged-out user: redirect to the generic account page
            "https://$shopDomain/account"
        }
    }


    /**
     * Retrieves the total quantity of items currently in the Shopify cart.
     *
     * This function performs a lightweight GraphQL query to fetch the total number
     * of items (across all line items) in the user's active Shopify cart.
     *
     * The result is delivered asynchronously via the provided callback.
     *
     * @param callback A function invoked with the total item count.
     *  - Returns `0` if the cart is empty, missing, or the query fails.
     */
    fun getTotalCartItems(callback: (Int) -> Unit) {

        // 🛑 Retrieve the stored cart ID
        val cartId = getCartId()
        if (cartId == null) {
            Timber.d("$TAG -> ❌ No cart found")
            callback.invoke(0)
            return
        }

        // ✅ Define the GraphQL query to fetch total cart quantity
        val query = Storefront.query { root ->
            root.cart(ID(cartId)) { cart ->
                cart.totalQuantity()
            }
        }

        // ✅ Execute the query asynchronously
        graphClient?.queryGraph(query)?.enqueue { result ->
            when (result) {

                // 🟢 Query succeeded — extract cart count
                is GraphCallResult.Success -> {
                    val cart = result.response.data?.cart
                    val count = cart?.totalQuantity ?: 0

                    // Safely invoke the callback on the main (UI) thread
                    if (context != null && Looper.myLooper() == Looper.getMainLooper()) {
                        context?.lifecycleScope?.launch(Dispatchers.Main) {
                            callback.invoke(count)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            callback.invoke(count)
                        }
                    }

                    Log.d("SHOPIFY", "🛒 Cart Count: $count")
                }

                // 🔴 Query failed — return 0 as fallback
                is GraphCallResult.Failure -> {
                    callback.invoke(0)
                    Log.e("SHOPIFY", "❌ Failed to fetch cart count", result.error)
                }
            }
        }
    }



    /**
     * Fetches detailed information about the specified Shopify cart.
     *
     * This function performs a GraphQL query to retrieve cart details including:
     * - Checkout URL
     * - Total quantity
     * - Line items (with variant, product, price, and image information)
     *
     * The response is parsed into a list of [CartItem] objects for easy use in the UI.
     *
     * @param cartId The unique ID of the Shopify cart.
     * @param onResult A callback returning a [Result]:
     *  - `List<CartItem>` containing all items in the cart.
     *  - A [Throwable] if the query or parsing fails.
     */
    fun getCartDetails(
        cartId: String?,
        onResult: (Result<List<CartItem>>) -> Unit
    ) {
        // 🛑 Validate input before making network call
        if (cartId.isNullOrEmpty()) {
            onResult(Result.failure(Exception("Cart ID cannot be null or empty")))
            return
        }

        // ✅ Define the GraphQL query for fetching cart details
        val query = Storefront.query { root ->
            root.cart(ID(cartId)) { cart ->
                cart.checkoutUrl()
                cart.totalQuantity()

                // Fetch up to 20 cart lines (you can increase as needed)
                cart.lines({ args -> args.first(20) }) { lineConnection ->
                    lineConnection.edges { edge ->
                        edge.node { lineItem ->
                            lineItem.id()
                            lineItem.quantity()

                            // Get variant and product details
                            lineItem.merchandise { merchandise ->
                                merchandise.onProductVariant { variant ->
                                    variant.title()
                                    variant.price { price ->
                                        price.amount()
                                        price.currencyCode()
                                    }

                                    variant.product { product ->
                                        product.title()
                                        product.description()
                                        product.featuredImage { image ->
                                            image.url()
                                        }
                                        product.options { option ->
                                            option.name()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ✅ Execute the query asynchronously using Shopify's GraphClient
        graphClient?.queryGraph(query)?.enqueue { result ->
            when (result) {

                // 🟢 Query successful
                is GraphCallResult.Success -> {
                    try {
                        val cart = result.response.data?.cart
                        val items = cart?.lines?.edges?.mapNotNull { edge ->
                            val line = edge.node
                            if (line.quantity <= 0) return@mapNotNull null // skip invalid lines

                            val variant = line.merchandise as? Storefront.ProductVariant ?: return@mapNotNull null
                            val product = variant.product
                            val optionTitle = variant.product.options.firstOrNull()?.name

                            // ✅ Construct your local data model (CartItem)
                            CartItem(
                                lineId = line.id.toString(),
                                variantTitle = optionTitle.orEmpty(),
                                quantity = line.quantity,
                                price = variant.price.amount.toDouble(),
                                imageUrl = product.featuredImage?.url.orEmpty(),
                                variantsId = variant.id.toString(),
                                currencyCode = variant.price.currencyCode.toString(),
                                variantName = variant.title,
                                title = product.title
                            )
                        } ?: emptyList()

                        onResult(Result.success(items))
                    } catch (e: Exception) {
                        // 🛑 Handle parsing or casting errors gracefully
                        onResult(Result.failure(e))
                    }
                }

                // 🔴 Network or GraphQL failure
                is GraphCallResult.Failure -> {
                    onResult(Result.failure(result.error))
                }
            }
        }
    }


    /**
     * Updates the quantity of a specific line item in a Shopify cart.
     *
     * This function performs a GraphQL mutation to update the quantity of an existing
     * line (product) within the specified cart.
     *
     * @param cartId The unique ID of the Shopify cart.
     * @param lineId The unique ID of the line item in the cart.
     * @param newQuantity The new quantity to set for this line item.
     * @param onResult A callback returning a [Result]:
     *  - `true` if the line was successfully updated.
     *  - A [Throwable] if the update failed (e.g., invalid IDs or Shopify error).
     */
    fun updateCartLineQuantity(
        cartId: String,
        lineId: String,
        newQuantity: Int,
        onResult: (Result<Boolean>) -> Unit
    ) {
        // ✅ Build the mutation for updating a cart line
        val mutation = Storefront.mutation { root ->
            root.cartLinesUpdate(
                ID(cartId),
                listOf(
                    Storefront.CartLineUpdateInput(ID(lineId))
                        .setQuantity(newQuantity) // Correct setter usage ✅
                )
            ) { payload ->
                // Request updated cart fields after quantity change
                payload.cart { cart ->
                    cart.totalQuantity() // Fetch updated total quantity
                }

                // Capture user or validation errors from Shopify
                payload.userErrors { error ->
                    error.field()
                    error.message()
                }
            }
        }

        // ✅ Execute the mutation asynchronously
        graphClient?.mutateGraph(mutation)?.enqueue { result ->
            when (result) {

                // 🟢 Mutation succeeded
                is GraphCallResult.Success -> {
                    val errors = result.response.data?.cartLinesUpdate?.userErrors
                    if (errors.isNullOrEmpty()) {
                        // Quantity updated successfully
                        onResult(Result.success(true))
                    } else {
                        // Shopify returned logical/validation errors
                        val errorMessage = errors.joinToString { it.message }
                        onResult(Result.failure(Exception(errorMessage)))
                    }
                }

                // 🔴 GraphQL or network failure
                is GraphCallResult.Failure -> {
                    onResult(Result.failure(result.error))
                }
            }
        }
    }


    /**
     * Removes a specific line item from a Shopify cart.
     *
     * This function performs a GraphQL mutation to remove a line (product) from
     * an existing cart on Shopify using its `cartId` and `lineId`.
     *
     * @param cartId The unique ID of the cart from which the line will be removed.
     * @param lineId The unique ID of the line (item) to remove from the cart.
     * @param onResult A callback returning a [Result]:
     *  - `true` if the line was successfully removed.
     *  - A [Throwable] if the operation failed (e.g., invalid cart ID or Shopify error).
     */
    fun removeCartLine(
        cartId: String,
        lineId: String,
        onResult: (Result<Boolean>) -> Unit
    ) {
        // ✅ Define the GraphQL mutation to remove a cart line
        val mutation = Storefront.mutation { root ->
            root.cartLinesRemove(
                ID(cartId), // Shopify expects IDs to be wrapped in the ID() type
                listOf(ID(lineId)) // Multiple line IDs can be passed; here we use one
            ) { payload ->
                // Request updated cart fields after removal (optional)
                payload.cart { cart ->
                    cart.totalQuantity() // Check remaining quantity in the cart
                }

                // Capture any user input or Shopify API validation errors
                payload.userErrors { error ->
                    error.field()
                    error.message()
                }
            }
        }

        // ✅ Execute the mutation asynchronously using Shopify's GraphClient
        graphClient?.mutateGraph(mutation)?.enqueue { result ->
            when (result) {

                // 🟢 Mutation succeeded
                is GraphCallResult.Success -> {
                    val errors = result.response.data?.cartLinesRemove?.userErrors

                    if (errors.isNullOrEmpty()) {
                        // Successfully removed item from the cart
                        onResult(Result.success(true))
                    } else {
                        // Shopify returned business logic errors (e.g., invalid lineId)
                        val errorMessage = errors.joinToString { it.message }
                        onResult(Result.failure(Exception(errorMessage)))
                    }
                }

                // 🔴 Network or GraphQL error occurred
                is GraphCallResult.Failure -> {
                    onResult(Result.failure(result.error))
                }
            }
        }
    }



    /**
     * Returns the currency symbol for a given ISO currency code.
     *
     * This function maps common three-letter currency codes (like USD, EUR, INR, etc.)
     * to their corresponding symbols. If the currency code is not recognized,
     * it simply returns the code itself.
     *
     * @param code The 3-letter ISO currency code (e.g., "USD", "INR", "EUR").
     * @return The matching currency symbol (e.g., "$", "₹", "€"), or the code itself if unknown.
     */
    fun currencySymbol(code: String): String {
        return when (code.uppercase()) { // normalize to uppercase for consistency
            "USD" -> "$"   // US Dollar
            "EUR" -> "€"   // Euro
            "INR" -> "₹"   // Indian Rupee
            "GBP" -> "£"   // British Pound
            "SGD" -> "S$"  // Singapore Dollar
            else -> code   // Fallback: return the original code if not recognized
        }
    }


    /**
     * Registers a new customer account on Shopify.
     *
     * This function performs a GraphQL mutation to create a new customer
     * using the provided email, password, and name details.
     *
     * @param onResult A callback returning a [Result]:
     *  - `true` if the customer was successfully registered.
     *  - A [Throwable] if registration failed (e.g., user already exists or invalid input).
     */
    fun registerCustomer(onResult: (Result<Boolean>) -> Unit) {

        // ✅ Prepare the customer input data for the GraphQL mutation
        val customerInput = Storefront.CustomerCreateInput(customerEmailId, customerPassword)
            .setFirstName(customerFirstName)
            .setLastName(customerLastName)

        // ✅ Define the GraphQL mutation for creating a customer
        val mutation = Storefront.mutation { root ->
            root.customerCreate(customerInput) { payload ->

                // Request the customer details to confirm creation
                payload.customer { customer ->
                    customer.id()
                    customer.firstName()
                    customer.lastName()
                    customer.email()
                }

                // Include user validation errors (e.g., invalid email or weak password)
                payload.customerUserErrors { error ->
                    error.field()
                    error.message()
                }
            }
        }

        // ✅ Execute the mutation asynchronously via Shopify GraphClient
        graphClient?.mutateGraph(mutation)?.enqueue { result ->
            when (result) {

                // 🟢 Registration successful
                is GraphCallResult.Success -> {
                    val errors = result.response.data?.customerCreate?.customerUserErrors

                    if (errors.isNullOrEmpty()) {
                        // Customer created successfully
                        onResult(Result.success(true))
                    } else {
                        // 🟠 Shopify returned validation or business logic errors
                        val errorMessage = errors.joinToString { it.message }
                        onResult(Result.failure(Exception(errorMessage)))
                    }
                }

                // 🔴 Mutation failed due to network or server issues
                is GraphCallResult.Failure -> {
                    onResult(Result.failure(result.error))
                }
            }
        }
    }


    /**
     * Adds a new address to the logged-in Shopify customer's account.
     *
     * This function performs a GraphQL mutation to create a new mailing address
     * associated with the current authenticated customer (using the stored access token).
     *
     * @param onResult A callback returning a [Result]:
     *  - `true` if the address was successfully added.
     *  - A [Throwable] if an error occurred (e.g., invalid token or Shopify validation error).
     */
    fun addCustomerAddress(
        onResult: (Result<Boolean>) -> Unit
    ) {
        // ✅ Build the input object for the address fields
        val addressInput = Storefront.MailingAddressInput()
            .setAddress1(address1)
            .setAddress2(address2)
            .setCity(city)
            .setProvince(province)
            .setZip(zip)
            .setCountry(country)
            .setPhone(phone)

        // ✅ Create the GraphQL mutation to add the customer address
        val mutation = Storefront.mutation { root ->
            val token = getCustomerAccessToken().toString() // Get the saved customer access token

            root.customerAddressCreate(token, addressInput) { payload ->
                // Request the returned address fields (for confirmation)
                payload.customerAddress { addr ->
                    addr.address1()
                    addr.city()
                    addr.country()
                }

                // Include potential user input validation errors
                payload.customerUserErrors { err ->
                    err.field()
                    err.message()
                }
            }
        }

        // ✅ Execute the mutation using the Shopify GraphClient
        graphClient?.mutateGraph(mutation)?.enqueue { result ->
            when (result) {

                // 🟢 Mutation succeeded
                is GraphCallResult.Success -> {
                    val errors = result.response.data?.customerAddressCreate?.customerUserErrors

                    if (errors.isNullOrEmpty()) {
                        // Address added successfully
                        onResult(Result.success(true))
                    } else {
                        // Shopify returned validation/user errors
                        val errorMessage = errors.joinToString { it.message }
                        onResult(Result.failure(Exception(errorMessage)))
                    }
                }

                // 🔴 Mutation failed (network or API error)
                is GraphCallResult.Failure -> {
                    onResult(Result.failure(result.error))
                }
            }
        }
    }


    /**
     * Logs in a Shopify customer using their email and password.
     *
     * This function performs a GraphQL mutation to request a new customer access token
     * from the Shopify Storefront API. If successful, it saves the access token locally
     * for future authenticated API calls.
     *
     * @param onResult A callback returning a [Result] that contains:
     *  - `true` if login was successful and a valid token was retrieved.
     *  - A [Throwable] with an error message if login failed.
     */
    fun loginCustomer(onResult: (Result<Boolean>) -> Unit) {
        // Prepare input for the Shopify GraphQL mutation
        val input = Storefront.CustomerAccessTokenCreateInput(customerEmailId, customerPassword)

        // Build the mutation to request an access token
        val mutation = Storefront.mutation { root ->
            root.customerAccessTokenCreate(input) { payload ->
                // Request the token and expiry fields
                payload.customerAccessToken { token ->
                    token.accessToken()
                    token.expiresAt()
                }
                // Include potential user errors for debugging or user feedback
                payload.customerUserErrors { error ->
                    error.field()
                    error.message()
                }
            }
        }

        // Execute the mutation asynchronously using Shopify's GraphClient
        graphClient?.mutateGraph(mutation)?.enqueue { result ->
            when (result) {

                // ✅ Login succeeded
                is GraphCallResult.Success -> {
                    val errors = result.response.data?.customerAccessTokenCreate?.customerUserErrors

                    if (errors.isNullOrEmpty()) {
                        // Retrieve and save the customer access token
                        val token = result.response.data
                            ?.customerAccessTokenCreate
                            ?.customerAccessToken
                            ?.accessToken

                        saveCustomerAccessToken(token)
                        onResult(Result.success(true)) // Notify success
                    } else {
                        // 🛑 Handle Shopify user errors (e.g., invalid credentials)
                        val errorMessage = errors.joinToString { it.message }
                        onResult(Result.failure(Exception(errorMessage)))
                    }
                }

                // ❌ Network or GraphQL call failed
                is GraphCallResult.Failure -> {
                    onResult(Result.failure(result.error))
                }
            }
        }
    }


    /**
     * Saves the Shopify customer access token securely in SharedPreferences.
     *
     * This function stores the given access token in the app’s private shared preferences
     * so it can be retrieved later for authenticated Shopify API calls.
     *
     * @param token The customer access token to save. If `null`, the existing token is overwritten with null.
     */
    private fun saveCustomerAccessToken(token: String?) {
        // Get reference to app's private SharedPreferences
        val prefs = context?.getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE)

        // Store the token asynchronously using apply()
        prefs?.edit()?.putString(CUSTOMER_ACCESS_TOKEN, token)?.apply()
    }

    /**
     * Retrieves the stored Shopify customer access token from SharedPreferences.
     *
     * @return The saved access token, or `null` if no token is stored.
     */
    private fun getCustomerAccessToken(): String? {
        // Get reference to app's private SharedPreferences
        val prefs = context?.getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE)

        // Return the saved token, or null if not found
        return prefs?.getString(CUSTOMER_ACCESS_TOKEN, null)
    }


    /**
     * Validates the current Shopify customer access token.
     *
     * This function checks whether the stored customer access token is valid by
     * performing a GraphQL query to retrieve customer details.
     *
     * @param onResult Callback that returns a [Result] containing:
     *  - `true` if the token is valid and the customer data is successfully retrieved.
     *  - `false` if the token is invalid or no customer data is returned.
     *  - A [Throwable] failure if a network or GraphQL error occurs.
     */
    fun validateCustomerAccessToken(
        onResult: (Result<Boolean>) -> Unit
    ) {
        // Build the GraphQL query to fetch customer details using the access token
        val query = Storefront.query { root ->
            val token = getCustomerAccessToken().toString()
            root.customer(token) { customer ->
                customer.id()
                customer.email()
                customer.firstName()
                customer.lastName()
            }
        }

        // Execute the query using the Shopify GraphClient
        graphClient?.queryGraph(query)?.enqueue { result ->
            when (result) {
                // ✅ Query succeeded — check if a valid customer was returned
                is GraphCallResult.Success -> {
                    val customer = result.response.data?.customer
                    if (customer != null) {
                        onResult(Result.success(true)) // Valid token
                    } else {
                        onResult(Result.success(false)) // Invalid or expired token
                    }
                }

                // ❌ Query failed — return the error to the callback
                is GraphCallResult.Failure -> {
                    onResult(Result.failure(result.error))
                }
            }
        }
    }


    /**
     * Shows a Toast message safely from any thread.
     *
     * This function ensures that the Toast is displayed on the main (UI) thread,
     * even if it's called from a background thread.
     *
     * @param message The text message to display in the Toast.
     */
    private fun showToast(message: String) {
        // Check if context is available and the current thread is the main thread
        if (context != null && Looper.myLooper() == Looper.getMainLooper()) {

            // Launch a coroutine on the main thread using lifecycleScope
            context?.lifecycleScope?.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            }

        } else {
            // If not on the main thread, post the Toast display to the main looper
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    mContext,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

}
