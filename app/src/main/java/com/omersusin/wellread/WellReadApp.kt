package com.omersusin.wellread

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WellReadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required one-time initialization for pdfbox-android
        PDFBoxResourceLoader.init(applicationContext)
    }
}
