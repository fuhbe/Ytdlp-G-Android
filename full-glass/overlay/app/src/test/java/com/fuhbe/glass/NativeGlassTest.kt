package com.fuhbe.glass

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the real upstream widgets and calls its unmodified format renderer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeGlassTest {
    @Test fun nativeVersionRemainsCompatibleWithTheUnmodifiedUpdater() {
        val versionNumber = BuildConfig.VERSION_NAME.replace("-beta", "").replace(".", "").padEnd(10, '0').toInt()
        assertTrue(versionNumber > 0)
        assertEquals("1.9.1.1", BuildConfig.VERSION_NAME)
    }

    private fun context(night: Boolean): Context {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-${if (night) "night" else "notnight"}-xxhdpi")
        return ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Blue)
    }

    @Test fun videoLabelsRemainVideoAndNativeCardSelectionWorksInBothThemes() {
        for (night in listOf(false, true)) {
            val ctx = context(night)
            val card = LayoutInflater.from(ctx).inflate(R.layout.format_item, null, false) as MaterialCardView
            UiUtil.populateFormatCard(ctx, card, Format(format_id = "137", container = "mp4",
                vcodec = "avc1", acodec = "none", format_note = "1080p", filesize = 24_000_000))
            assertEquals("MP4", card.findViewById<TextView>(R.id.container).text.toString())
            assertEquals("1080P", card.findViewById<TextView>(R.id.format_note).text.toString())
            assertFalse(card.findViewById<TextView>(R.id.format_note).text.contains("MP3"))
            for (id in listOf(R.id.container, R.id.codec, R.id.file_size)) {
                val label = card.findViewById<TextView>(id)
                val fill = label.backgroundTintList!!.getColorForState(label.drawableState, 0)
                assertTrue("Badge text contrast: $id, night=$night",
                    ColorUtils.calculateContrast(label.currentTextColor, fill) >= 4.5)
            }
            assertTrue(card.isCheckable)
            val unselected = card.cardBackgroundColor.getColorForState(card.drawableState, 0)
            card.isChecked = true
            assertTrue(card.isChecked)
            val selected = card.cardBackgroundColor.getColorForState(card.drawableState, 0)
            assertNotEquals("Selected cards must remain visibly distinguishable", unselected, selected)
            card.isChecked = false
            assertFalse(card.isChecked)
        }
    }

    @Test fun textAndSecondaryLabelsStayReadableInBothThemes() {
        for (night in listOf(false, true)) {
            val ctx = context(night)
            val surface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, 0)
            val primary = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0)
            val secondary = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
            assertTrue("Primary contrast", ColorUtils.calculateContrast(primary, surface) >= 4.5)
            assertTrue("Secondary contrast", ColorUtils.calculateContrast(secondary, surface) >= 4.5)
        }
    }

    @Test fun theOriginalHighContrastSettingStillRemovesTranslucentSurfaces() {
        for (night in listOf(false, true)) {
            val ctx = context(night)
            ctx.theme.applyStyle(R.style.Pure, true)
            val value = TypedValue()
            assertTrue(ctx.theme.resolveAttribute(R.attr.liquidCardFill, value, true))
            assertEquals(if (night) Color.BLACK else Color.WHITE, ctx.getColor(value.resourceId))
        }
    }

    @Test fun renderRealNativeFormatCardsWithGlassResources() {
        for (night in listOf(false, true)) {
            val ctx = context(night)
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 32, 24, 32)
                background = ctx.getDrawable(R.drawable.liquid_wallpaper)
            }
            for (format in listOf(
                Format(format_id = "137", container = "mp4", vcodec = "avc1", acodec = "none", format_note = "1080p", filesize = 24_000_000),
                Format(format_id = "136", container = "mp4", vcodec = "avc1", acodec = "none", format_note = "720p", filesize = 14_000_000),
                Format(format_id = "251", container = "webm", vcodec = "none", acodec = "opus", format_note = "Audio", tbr = "160k", filesize = 4_000_000)
            )) {
                val card = LayoutInflater.from(ctx).inflate(R.layout.format_item, root, false) as MaterialCardView
                UiUtil.populateFormatCard(ctx, card, format)
                root.addView(card)
            }
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            assertTrue(root.height > 0)
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val directory = File("build/reports/glass-screenshots").apply { mkdirs() }
            File(directory, "native-formats-${if (night) "dark" else "light"}.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
