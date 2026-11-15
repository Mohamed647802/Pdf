package com.pdffusion.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

class PdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        File(cacheDir, "in").deleteRecursively()
        File(cacheDir, "out").deleteRecursively()
    }
}
