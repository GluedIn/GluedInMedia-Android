package com.gluedin.media.and

import android.app.Application
import com.facebook.drawee.backends.pipeline.Fresco
import com.gluedin.GluedInInitializer
import com.gluedin.leaderboard.BuildConfig
import com.gluedin.presentation.networkImage.FrescoInitializer
import java.lang.ref.WeakReference

class GluedInApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        GluedInInitializer.initSdk(this)
        if (Fresco.hasBeenInitialized().not()) {
            FrescoInitializer.initialize(WeakReference(this), BuildConfig.DEBUG)
        }
    }
}