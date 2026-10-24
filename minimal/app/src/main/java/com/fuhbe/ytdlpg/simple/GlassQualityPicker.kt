package com.fuhbe.ytdlpg.simple

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Presentation only. Receives the original options and callback verbatim;
 * never inspects media, adds resolutions, or constructs download choices. */
@Composable internal fun GlassQualityPicker(options:List<Int>,selected:Int,choose:(Int)->Unit) {
    val state=remember { GlassMorphMenuState() }
    var anchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
        val point=coordinates.localToScreen(androidx.compose.ui.geometry.Offset.Zero)
        val bounds=androidx.compose.ui.geometry.Rect(point,coordinates.size.toSize())
        if(anchor!=bounds)anchor=bounds
    }) {
        BackHandler(state.mounted) { state.close() }
        GlassQualitySurface(onClick=state::toggle,modifier=Modifier
            .testTag(if(state.handoff) "glass-quality-origin-hidden" else "glass-quality-trigger")
            .graphicsLayer { alpha=if(state.handoff)0f else 1f }
            .then(if(state.handoff)Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier)) {
            GlassQualityTriggerContent(selected) { 0f }
        }
        GlassMorphMenu(state,anchor,options,selected,choose) {
            GlassQualityTriggerContent(selected) { state.progress.value.coerceIn(0f,1f)*180f }
        }
    }
}
@Composable private fun RowScope.GlassQualityTriggerContent(selected:Int,angle:()->Float) {
    Icon(Icons.Default.HighQuality,null,Modifier.size(23.dp));Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text(if(selected==0) "Best quality" else "${selected}p",fontWeight=FontWeight.SemiBold)
        Text(if(selected==0) "Up to 4K, when available" else "Up to the selected resolution",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Icon(Icons.Default.ExpandMore,"Choose quality",Modifier.graphicsLayer { rotationZ=angle() })
}

/** Fit beside the existing trigger, prefer below then above, clamp to window.
 * Popup owns dismissal/focus, so keyboard, Back and outside taps stay usable. */
internal class GlassQualityPosition(private val gap:Int):PopupPositionProvider {
    var originY by mutableFloatStateOf(0f);private set
    override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,
        layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
        val below=anchorBounds.bottom+gap
        val fitsBelow=below+popupContentSize.height<=windowSize.height-gap
        originY=if(fitsBelow) 0f else 1f
        val y=if(fitsBelow) below else anchorBounds.top-gap-popupContentSize.height
        val x=if(layoutDirection==LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right-popupContentSize.width
        return IntOffset(x.coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0,(windowSize.height-popupContentSize.height).coerceAtLeast(0)))
    }

}

@Composable internal fun GlassQualitySurface(onClick:()->Unit,modifier:Modifier=Modifier,
    material:Boolean=true,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressure=animateFloatAsState(if(pressed) 1f else 0f,spring(.65f,500f),label="Quality glass pressure")
    Surface(onClick=onClick,enabled=enabled,interactionSource=interaction,color=Color.Transparent,
        shape=RoundedCornerShape(18.dp),modifier=modifier.fillMaxWidth().heightIn(min=54.dp)
            .graphicsLayer { scaleX=1f-pressure.value*.018f;scaleY=1f+pressure.value*.015f }
            .then(if(material) Modifier.liquidGlass(RoundedCornerShape(18.dp),prominent=true,interaction={pressure.value}) else Modifier)) {
        Row(Modifier.padding(horizontal=10.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,content=content)
    }
}
