package com.gluedin.media.and

import com.gluedin.domain.entities.curation.RailItem
import com.gluedin.domain.entities.curation.WidgetData

interface CarouselListener {
    fun onItemClick(item: RailItem, railItems: WidgetData)
}