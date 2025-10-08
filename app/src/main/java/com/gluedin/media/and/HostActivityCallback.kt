package com.gluedin.media.and

import com.gluedin.GluedInInitializer
import com.gluedin.domain.entities.curation.RailItem
import com.gluedin.domain.entities.curation.WidgetData

interface HostActivityCallback {
    fun getGluedInConfigurations(): GluedInInitializer.Configurations?
    fun launchSDK(item: RailItem, railItems: WidgetData)
}