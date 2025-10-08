package com.gluedin.media.and.shopify

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gluedin.base.presentation.constants.PlusSawDataHolder.isDarkTheme
import com.gluedin.media.and.R
import com.gluedin.media.and.databinding.ActivityWebViewBinding

/**
 * Activity to display web content inside a WebView.
 *
 * This activity handles:
 * - Displaying a webpage using WebView.
 * - Managing cookies for user session persistence.
 * - Showing a loading indicator while the page loads.
 * - Dynamically applying bottom margins for system bars.
 * - Setting status bar color and theme dynamically.
 */
class WebViewActivity : AppCompatActivity() {

    private var binding: ActivityWebViewBinding? = null
    private var cookieManager: CookieManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)

        // Apply bottom margin dynamically to handle gesture navigation insets
        applySystemBottomMargin()

        setContentView(binding!!.root)

        // Get URL and title from Intent extras
        val url = intent.getStringExtra("url")
        val title = intent.getStringExtra("title")

        // Set the toolbar title
        binding?.title?.text = title

        // Initialize and load the WebView
        setWebView(url)

        // Handle back button click
        binding?.imgBack?.setOnClickListener {
            finish()
        }
    }

    /**
     * Dynamically applies system bottom margin to prevent content overlap with navigation bar.
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

    /**
     * Configures and loads the WebView with the provided URL.
     */
    private fun setWebView(url: String?) {

        // ✅ Initialize cookie manager for session handling
        cookieManager = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding?.webView, true)
        }

        // ✅ General WebView configuration
        binding?.webView?.apply {
            scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
            isScrollbarFadingEnabled = false
            setInitialScale(1)
        }

        // ✅ Configure WebSettings
        val settings: WebSettings? = binding?.webView?.settings
        settings?.apply {
            javaScriptEnabled = true               // Enable JavaScript
            builtInZoomControls = true             // Enable pinch-to-zoom
            displayZoomControls = false            // Hide default zoom UI
            loadWithOverviewMode = true            // Scale page to fit view
            useWideViewPort = true                 // Use full width of viewport
            domStorageEnabled = true               // Enable localStorage support
            cacheMode = WebSettings.LOAD_DEFAULT   // Default caching mode
        }

        // ⚙️ Adjust zoom controls for older devices
        if (Build.VERSION.SDK_INT > 10) {
            settings?.displayZoomControls = true
        }

        // ⚠️ Deprecated but required for cookie persistence on old APIs
        CookieSyncManager.createInstance(this)
        CookieSyncManager.getInstance().startSync()

        // ✅ Assign WebViewClient to handle loading and progress
        binding?.webView?.webViewClient = WebClient(binding?.ALERTLAYOUT)

        // ✅ Load the target URL
        binding?.webView?.loadUrl(url.orEmpty())
    }

    /**
     * Custom WebViewClient to show/hide progress view during page load.
     */
    internal class WebClient(private var progress: LinearLayout?) : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            // Show loading indicator
            if (progress?.isShown == false) progress?.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            // Hide loading indicator
            if (progress?.isShown == true) progress?.visibility = View.GONE
        }
    }

    /**
     * Sync cookies when the Activity is paused (for older Android versions).
     */
    override fun onPause() {
        super.onPause()
        CookieSyncManager.getInstance().sync()
    }

    /**
     * Restore UI configurations on resume.
     */
    override fun onResume() {
        super.onResume()
        updateStatusBar()
    }

    /**
     * Extension function to update status bar color and theme dynamically.
     *
     * @param backgroundColor Optional custom background color (defaults to app_black).
     */
    fun Context.updateStatusBar(@ColorInt backgroundColor: Int? = null) {
        (this as AppCompatActivity).apply {
            val resolvedColor = backgroundColor ?: ContextCompat.getColor(this, R.color.app_black)
            val darkIcons = isDarkTheme.not()
            window.statusBarColor = resolvedColor

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+ use WindowInsetsController
                window.setDecorFitsSystemWindows(true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = darkIcons
            } else {
                // Backward-compatible method for status bar icon color
                var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                flags = if (darkIcons) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                window.decorView.systemUiVisibility = flags
            }
        }
    }
}
