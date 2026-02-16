package com.gluedin.media.and

import android.app.Application
import com.facebook.drawee.backends.pipeline.Fresco
import com.gluedin.GluedInInitializer
import com.gluedin.leaderboard.BuildConfig
import com.gluedin.presentation.networkImage.FrescoInitializer
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.lang.ref.WeakReference

class GluedInApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        GluedInInitializer.initSdk(this)
        if (Fresco.hasBeenInitialized().not()) {
            FrescoInitializer.initialize(WeakReference(this), BuildConfig.DEBUG)
        }
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(listOf("set_the_test_device_id"))
            .build()

        // this is used for ads
        MobileAds.setRequestConfiguration(configuration)
        MobileAds.initialize(this)
    }
}