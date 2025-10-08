package com.gluedin.media.and.viewHolder

import androidx.recyclerview.widget.RecyclerView
import com.gluedin.domain.entities.curation.RailItem
import com.gluedin.media.and.databinding.SeriesItemBinding
import com.gluedin.presentation.utils.ViewUtils

// ViewHolder for displaying a "Series" item inside a RecyclerView
class AppSeriesViewHolder(private val binding: SeriesItemBinding) : RecyclerView.ViewHolder(binding.root) {

    // Context reference from the root view
    private val context = binding.root.context

    // Bind series data (RailItem) to the view
    fun bind(item: RailItem) {
        with(binding) {
            // Calculate available screen width (minus some dp padding)
            val screenWidth = context.resources.displayMetrics.widthPixels -
                    ViewUtils.dpToPx(context, 44)

            // Dynamically calculate thumbnail size
            val width = (screenWidth / 3.7).toInt()
            val height = width * 1.773

            // Apply width & height to the placeholder view
            binding.placeholder.layoutParams.width = width
            binding.placeholder.layoutParams.height = height.toInt()

            // Load thumbnail if available
            if (item.thumbnail.isNotEmpty()) {
                placeholder.setImageURI(item.thumbnail)
            }
        }
    }
}
