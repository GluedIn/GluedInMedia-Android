package com.gluedin.media.and

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.gluedin.GluedInInitializer
import com.gluedin.domain.entities.curation.RailItem
import com.gluedin.domain.entities.curation.WidgetData
import com.gluedin.media.and.databinding.ActivityMainBinding
import com.gluedin.media.and.fragment.HomeFragment
import com.gluedin.presentation.enum.ContentType
import com.gluedin.presentation.utils.DataUtility
import com.gluedin.usecase.constants.GluedInConstants
import kotlinx.coroutines.launch
import java.util.UUID

// Main Activity that hosts HomeFragment and initializes GluedIn SDK
class MainActivity : BaseActivity(), HostActivityCallback {
    private lateinit var binding: ActivityMainBinding

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
    override fun init() {
        val fragment = HomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainerView.id, fragment)
            .commit()

        binding.progressBar.isVisible = false

        // Example button click to launch SDK manually
        binding.thirdItem.setOnClickListener {
            launch(null, null, null, null, GluedInConstants.CarouselType.NONE, false)
        }
    }


    // Required for HostActivityCallback
    override fun getGluedInConfigurations(): GluedInInitializer.Configurations? {
        return sdkConfigurations
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
            var isDisableFullSDK: Boolean = false

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

            launch(
                seriesId,
                episodeNumber,
                selectedRailId,
                listOfData,
                carouselType,
                isDisableFullSDK
            )
        }
    }
}