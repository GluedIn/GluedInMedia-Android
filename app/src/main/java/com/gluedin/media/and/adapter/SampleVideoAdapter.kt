package com.gluedin.media.and.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gluedin.domain.entities.feed.VideoInfo
import com.gluedin.media.and.databinding.SampleVerticalWidgetItemBinding
import com.gluedin.presentation.utils.ViewUtils.dpToPx
import com.gluedin.presentation.utils.extensions.loadImageFromURL
// RecyclerView Adapter for displaying a list of sample videos
class SampleVideoAdapter : RecyclerView.Adapter<SampleVideoAdapter.VerticalViewHolder>() {

    // Listener to handle click events on videos
    private var listener: SampleVideoListener? = null

    // List to store all video data
    private var itemList = mutableListOf<VideoInfo>()

    // Context reference
    private lateinit var context: Context

    // Clear the old list and reset with new data
    @SuppressLint("NotifyDataSetChanged")
    fun resetDataList(list: List<VideoInfo>) {
        itemList.clear()
        itemList.addAll(list)
        // Notify RecyclerView that the whole list has changed
        notifyDataSetChanged()
    }

    // Inflate the layout for each item (ViewHolder creation)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        context = parent.context
        val binding = SampleVerticalWidgetItemBinding.inflate(inflater, parent, false)
        return VerticalViewHolder(binding)
    }

    // Bind data to the ViewHolder at the given position
    override fun onBindViewHolder(holder: VerticalViewHolder, position: Int) {
        val item = itemList[position]
        holder.bind(item, position)
    }

    // Inner ViewHolder class that represents each video item
    inner class VerticalViewHolder(private val binding: SampleVerticalWidgetItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Bind video data to the view
        fun bind(data: VideoInfo, position: Int) {
            // Calculate thumbnail width and height dynamically based on screen size
            val screenWidth = context.resources.displayMetrics.widthPixels - dpToPx(context, 30)
            val width = (screenWidth / 3.7).toInt()
            val height = width * 1.773

            // Apply width and height to the thumbnail view
            binding.videoThumbnail.layoutParams.width = width
            binding.videoThumbnail.layoutParams.height = height.toInt()

            with(binding) {
                // Load thumbnail image from the given URL
                videoThumbnail.loadImageFromURL(data.thumbnailUrl)

                // Set click listener for the item
                itemView.setOnClickListener {
                    listener?.onVideoClick(position, data.videoId)
                }
            }
        }
    }

    // Return total number of items in the list
    override fun getItemCount(): Int {
        return itemList.size
    }

    // Set the click listener from outside the adapter
    fun setListener(listener: SampleVideoListener) {
        this.listener = listener
    }
}
