package com.gluedin.media.and.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.gluedin.GluedInInitializer
import com.gluedin.domain.entities.curation.RailItem
import com.gluedin.domain.entities.curation.WidgetData
import com.gluedin.media.and.AppConstants
import com.gluedin.media.and.CarouselListener
import com.gluedin.media.and.HostActivityCallback
import com.gluedin.media.and.MicroCommunityActivity
import com.gluedin.media.and.adapter.PopularComedyAdapter
import com.gluedin.media.and.databinding.FragmentHomeBinding
import com.gluedin.media.and.adapter.StopActionAdapter
import com.gluedin.media.and.adapter.VideoAdapter
import com.gluedin.usecase.discover.DiscoverInteractor

/**
 * A simple [Fragment] subclass.
 * Use the [HomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
// HomeFragment shows multiple rails (carousels, series, stories, movies, comedy, etc.)
// Implements CarouselListener to handle item clicks
class HomeFragment : Fragment(), CarouselListener {

    // ViewBinding for accessing UI elements
    private var binding: FragmentHomeBinding? = null

    // Callback to communicate with Host Activity
    private var callback: HostActivityCallback? = null

    // Holds GluedIn SDK configurations
    private var gluedInConfigurations: GluedInInitializer.Configurations? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // You can initialize non-UI things here
    }

    // Attach fragment to its host activity and get callback reference
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is HostActivityCallback) {
            callback = context
        }
    }

    // Inflate the fragment layout with ViewBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding?.root
    }

    // Called after the view is created → load UI and set listeners
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadView()
        onClick()
    }

    // Load all rails and configurations
    private fun loadView() {
        gluedInConfigurations = callback?.getGluedInConfigurations()
        loadActionView()
        loadPopularComedy()
        loadMoviesView()
        loadCarousel()
        loadSeries()
        loadStory()
    }

    // Load Carousel rail from API and bind it to RecyclerView
    private fun loadCarousel() {
        val interaction = DiscoverInteractor()
        interaction.getCuratedRailDetails(AppConstants.CAROUSEL_ID) { throwable, widgetDetailResponse, stringResponse ->
            widgetDetailResponse?.data?.let {
                binding?.railTwoTitle?.text = it.railName
                val layoutManager =
                    LinearLayoutManager(context, GridLayoutManager.HORIZONTAL, false)

                val railAdapter = VideoAdapter(this, it)
                binding?.videoRail?.layoutManager = layoutManager
                binding?.videoRail?.adapter = railAdapter
                binding?.railTwo?.isVisible = true
            }
        }
    }

    // Load Series rail
    private fun loadSeries() {
        val interaction = DiscoverInteractor()
        interaction.getCuratedRailDetails(AppConstants.SERIES_ID) { throwable, widgetDetailResponse, stringResponse ->
            widgetDetailResponse?.data?.let {
                binding?.railSeriesTitle?.text = it.railName
                val layoutManager =
                    LinearLayoutManager(context, GridLayoutManager.HORIZONTAL, false)

                val railAdapter = VideoAdapter(this, it)
                binding?.railSeriesRecyclerView?.layoutManager = layoutManager
                binding?.railSeriesRecyclerView?.adapter = railAdapter
                binding?.railSeries?.isVisible = true
            }
        }
    }

    // Load Stories rail
    private fun loadStory() {
        val interaction = DiscoverInteractor()
        interaction.getCuratedRailDetails(AppConstants.STORY_ID) { throwable, widgetDetailResponse, stringResponse ->
            widgetDetailResponse?.data?.let {
                binding?.railStoriesTitle?.text = it.railName
                val layoutManager =
                    LinearLayoutManager(context, GridLayoutManager.HORIZONTAL, false)

                val railAdapter = VideoAdapter(this, it)
                binding?.railStoriesRecyclerView?.layoutManager = layoutManager
                binding?.railStoriesRecyclerView?.adapter = railAdapter
                binding?.railStories?.isVisible = true
            }
        }
    }

    // Handle click listeners for static UI parts
    private fun onClick() {
        binding?.railOne?.setOnClickListener {
            startActivity(Intent(activity, MicroCommunityActivity::class.java))
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            HomeFragment().apply {
                // Can pass arguments here if needed in the future
            }
    }

    // Load static rail: Non-stop action
    private fun loadActionView() {
        binding?.nonStopActionRail?.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        binding?.nonStopActionRail?.adapter = StopActionAdapter()
    }

    // Load static rail: Popular Comedy
    private fun loadPopularComedy() {
        binding?.railComedyRecyclerView?.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        binding?.railComedyRecyclerView?.adapter = PopularComedyAdapter()
    }

    // Load static rail: Movies
    private fun loadMoviesView() {
        binding?.railMoviesRecyclerView?.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        binding?.railMoviesRecyclerView?.adapter = PopularComedyAdapter()
    }

    // Callback when an item inside a rail is clicked
    override fun onItemClick(item: RailItem, railItems: WidgetData) {
        // Delegate the action to host activity (SDK launch or detail view)
        callback?.launchSDK(item, railItems)
    }
}
