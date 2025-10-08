package com.gluedin.media.and.viewHolder

import androidx.recyclerview.widget.RecyclerView
import com.gluedin.domain.entities.curation.RailItem
import com.gluedin.media.and.databinding.CarouselRailItemBinding

// ViewHolder for displaying a "Video" item inside a RecyclerView
class VideoViewHolder(private val binding: CarouselRailItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    // Bind video data (RailItem) to the UI
    fun bind(item: RailItem, position: Int) {
        with(binding) {
            // Load thumbnail if available
            if (item.thumbnail.isNotEmpty()) {
                placeholder.setImageURI(item.thumbnail)
                plusSawProfileLoveCount.text = item.likeCount.toString()
            }
        }
    }
}
