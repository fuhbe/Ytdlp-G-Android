package com.fuhbe.ytdlpg.simple

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31],application=SimpleApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassWallpaperTest {
    @Test fun themesSelectDifferentActualResourcesWithDistinctPixels() {
        val resources=RuntimeEnvironment.getApplication().resources
        assertEquals(R.drawable.glass_wallpaper_light,glassWallpaperResource(false))
        assertEquals(R.drawable.glass_wallpaper_dark,glassWallpaperResource(true))
        val light=GlassWallpaperCache.load(resources,glassWallpaperResource(false),411,1060)
        val dark=GlassWallpaperCache.load(resources,glassWallpaperResource(true),411,1060)
        assertNotEquals(light.getPixel(light.width/2,light.height/2),dark.getPixel(dark.width/2,dark.height/2))
        assertTrue(light.width<=540 && light.height<=1200)
        assertTrue(dark.width<=540 && dark.height<=1200)
    }
    @Test fun repeatedProgressIndependentRequestsReuseTheSameThemeBitmap() {
        val resources=RuntimeEnvironment.getApplication().resources
        val id=glassWallpaperResource(false)
        val first=GlassWallpaperCache.load(resources,id,411,1060)
        assertSame(first,GlassWallpaperCache.load(resources,id,411,1060))
        GlassWallpaperCache.load(resources,glassWallpaperResource(true),411,1060)
        assertSame(first,GlassWallpaperCache.load(resources,id,411,1060))
    }
}
