package com.gluedin.media.and.viewHolder

import androidx.recyclerview.widget.RecyclerView
import com.gluedin.domain.entities.curation.RailItem
import com.gluedin.media.and.databinding.StoryItemBinding

// ViewHolder for displaying a "Story" item inside a RecyclerView
class AppStoryViewHolder(private val binding: StoryItemBinding) : RecyclerView.ViewHolder(binding.root) {

    // Bind story data (RailItem) to the view
    fun bind(item: RailItem) {
        with(binding) {
            // Set the story username text
            storyUserName.text = item.firstName

            // Check if the story thumbnail is available
            if (item.thumbnail.isNotEmpty()) {
                // Load thumbnail image into the story image view
                storyUserImg.setImageURI(item.thumbnail)
            } else {
                // If no thumbnail, fallback to user's profile image (if available)
                if (item.profile.profileImageUrl.isNotEmpty()) {
                    storyUserImg.setImageURI(item.profile.profileImageUrl)
                }
            }
        }
    }
}
