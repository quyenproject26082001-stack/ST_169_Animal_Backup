package com.animal.avatar.charactor.maker.core.utils

import android.content.ComponentCallbacks2
import com.bumptech.glide.Glide
import com.lvt.ads.util.AdsApplication
import com.lvt.ads.util.AppOpenManager
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.ui.splash.SplashActivity
import kotlin.jvm.java

class App : AdsApplication() {


    override fun onCreate() {
        super.onCreate()
        AppOpenManager.getInstance().disableAppResumeWithActivity(SplashActivity::class.java)
    }

    override fun enableAdsResume(): Boolean {
        return true
    }

    override fun getListTestDeviceId(): MutableList<String>? {
        return null
    }

    override fun getResumeAdId(): String {
        return getString(R.string.open_resume)
    }

    override fun buildDebug(): Boolean {
        return true
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Glide.get(this).clearMemory()
            }
        }
    }
}