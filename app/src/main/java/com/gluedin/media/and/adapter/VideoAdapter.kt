package com.gluedin.media.and.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gluedin.domain.entities.curation.WidgetData
import com.gluedin.media.and.AppConstants
import com.gluedin.media.and.CarouselListener
import com.gluedin.media.and.AppWidgetType
import com.gluedin.media.and.databinding.CarouselRailItemBinding
import com.gluedin.media.and.databinding.SeriesItemBinding
import com.gluedin.media.and.databinding.StoryItemBinding
import com.gluedin.media.and.viewHolder.AppSeriesViewHolder
import com.gluedin.media.and.viewHolder.AppStoryViewHolder
import com.gluedin.media.and.viewHolder.VideoViewHolder

// RecyclerView Adapter that handles different types of items: Video, Story, and Series
class VideoAdapter(
    private val listener: CarouselListener, // Listener to handle item clicks
    private val railItems: WidgetData       // Data source containing items and type info
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Create ViewHolder based on the item type
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            // Video item layout
            AppConstants.VIDEO_VIEW_TYPE -> {
                val inflater = LayoutInflater.from(parent.context)
                val binding = CarouselRailItemBinding.inflate(inflater, parent, false)
                VideoViewHolder(binding)
            }

            // Story item layout
            AppConstants.STORY_VIEW_TYPE -> {
                val inflater = LayoutInflater.from(parent.context)
                val binding = StoryItemBinding.inflate(inflater, parent, false)
                AppStoryViewHolder(binding)
            }

            // Series item layout
            AppConstants.SERIES_VIEW_TYPE -> {
                val inflater = LayoutInflater.from(parent.context)
                val binding = SeriesItemBinding.inflate(inflater, parent, false)
                AppSeriesViewHolder(binding)
            }

            // Default → fall back to Video
            else -> {
                val inflater = LayoutInflater.from(parent.context)
                val binding = CarouselRailItemBinding.inflate(inflater, parent, false)
                VideoViewHolder(binding)
            }
        }
    }

    // Bind data to the correct ViewHolder
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = railItems.data[position]

        when (holder) {
            is VideoViewHolder -> {
                // Bind video item
                holder.bind(item, position)
            }
            is AppStoryViewHolder -> {
                // Bind story item
                holder.bind(item)
            }
            is AppSeriesViewHolder -> {
                // Bind series item
                holder.bind(item)
            }
        }

        // Handle click event for all types of items
        holder.itemView.setOnClickListener {
            listener.onItemClick(item, railItems)
        }
    }

    // Decide the view type of the item based on WidgetData type
    override fun getItemViewType(position: Int): Int {
        return when (railItems.type) {
            AppWidgetType.VIDEO.value -> AppConstants.VIDEO_VIEW_TYPE
            AppWidgetType.STORY.value -> AppConstants.STORY_VIEW_TYPE
            AppWidgetType.SERIES.value -> AppConstants.SERIES_VIEW_TYPE
            else -> AppConstants.VIDEO_VIEW_TYPE // Default fallback
        }
    }

    // Total number of items in the list
    override fun getItemCount(): Int {
        return railItems.data.size
    }
}
