package com.fuhbe.ytdlpg.simple

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

internal fun glassWallpaperResource(dark:Boolean):Int =
    if(dark) R.drawable.glass_wallpaper_dark else R.drawable.glass_wallpaper_light

/** At most 8 MiB retained across both themes/sizes. Bitmap decoding is off the
 * UI thread. Original PNGs are never stretched, modified, or used as glass textures. */
internal object GlassWallpaperCache {
    private val cache = object:LruCache<String,Bitmap>(8*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    fun load(resources:Resources,id:Int,width:Int,height:Int):Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true;inScaled=false }
        BitmapFactory.decodeResource(resources,id,bounds)
        // Wallpaper detail is intentionally capped at 540x1200 (~2.6 MiB/theme).
        val desired=max(width.toFloat()/bounds.outWidth,height.toFloat()/bounds.outHeight).coerceIn(.1f,.5f)
        var sample=1
        while(sample<8 && 1f/(sample*2)>=desired) sample*=2
        val key="$id:$sample"
        return synchronized(cache) {
            cache.get(key) ?: checkNotNull(BitmapFactory.decodeResource(resources,id,
                BitmapFactory.Options().apply { inScaled=false;inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888 }))
                .also { cache.put(key,it) }
        }
    }
}

@Composable internal fun GlassWallpaper(dark:Boolean,modifier:Modifier=Modifier) {
    val resources=LocalContext.current.resources
    val density=LocalDensity.current
    val id=glassWallpaperResource(dark)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Cap the longest decode request without changing the viewport's crop.
        val rawWidth=with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val rawHeight=with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val factor=min(1f,1920f/max(rawWidth,rawHeight))
        val width=(rawWidth*factor).toInt().coerceAtLeast(1)
        val height=(rawHeight*factor).toInt().coerceAtLeast(1)
        val bitmap by produceState<ImageBitmap?>(null,id,width,height) {
            value=withContext(Dispatchers.IO) { GlassWallpaperCache.load(resources,id,width,height).asImageBitmap() }
        }
        bitmap?.let { Image(it,null,Modifier.fillMaxSize().testTag(if(dark) "glass-wallpaper-dark" else "glass-wallpaper-light"),contentScale=ContentScale.Crop) }
    }
}
