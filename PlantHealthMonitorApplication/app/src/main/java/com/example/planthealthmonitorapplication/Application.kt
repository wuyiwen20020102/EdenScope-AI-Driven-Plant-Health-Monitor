package com.example.planthealthmonitorapplication

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator

class PlantHealthMonitorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Backend.initialize(applicationContext)
        configureGlide()
    }
    
    private fun configureGlide() {
        val calculator = MemorySizeCalculator.Builder(this)
            .setMemoryCacheScreens(3f) // Default is 2
            .build()
            
        Glide.init(this, GlideBuilder()
            .setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong() * 2))
            .setBitmapPool(LruBitmapPool(calculator.bitmapPoolSize.toLong() * 2))
        )
    }
    
    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }
}