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
    var expanded by remember { mutableStateOf(false) }
    val visibility=remember { MutableTransitionState(false) }
    visibility.targetState=expanded
    val overlay=LocalGlassOverlaySource.current
    val open=visibility.currentState || visibility.targetState
    DisposableEffect(overlay,open) {
        if(open) overlay?.let { it.users++ }
        onDispose { if(open) overlay?.let { it.users=(it.users-1).coerceAtLeast(0) } }
    }
    val source=if(overlay?.ready==true) overlay.backdrop else LocalGlassBackdrop.current
    var anchorWidth by remember { mutableIntStateOf(0) }
    val density=LocalDensity.current
    val configuration=LocalConfiguration.current
    val gap=with(density) { 6.dp.roundToPx() }
    val provider=remember(gap) { GlassQualityPosition(gap) }
    Box(Modifier.fillMaxWidth().onSizeChanged { anchorWidth=it.width }) {
    BackHandler(expanded) { expanded=false }
    val angle=animateFloatAsState(if(expanded) 180f else 0f,spring(.8f,500f),label="Quality disclosure")
    GlassQualitySurface(onClick={expanded=!expanded},modifier=Modifier.testTag("glass-quality-trigger")) {
        Icon(Icons.Default.HighQuality,null,Modifier.size(23.dp));Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if(selected==0) "Best quality" else "${selected}p",fontWeight=FontWeight.SemiBold)
            Text(if(selected==0) "Up to 4K, when available" else "Up to the selected resolution",
                fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ExpandMore,"Choose quality",Modifier.graphicsLayer { rotationZ=angle.value })
    }
    if(visibility.currentState || visibility.targetState) {
        Popup(popupPositionProvider=provider,onDismissRequest={expanded=false},
            properties=PopupProperties(focusable=true)) {
            AnimatedVisibility(visibility,
                enter=fadeIn(tween(140))+scaleIn(spring(.8f,550f),initialScale=.88f,
                    transformOrigin=TransformOrigin(.5f,provider.originY)),
                exit=fadeOut(tween(130))+scaleOut(tween(170),targetScale=.94f,
                    transformOrigin=TransformOrigin(.5f,provider.originY))) {
                Column(Modifier.padding(8.dp)
                    .width(with(density) { anchorWidth.toDp() }.coerceAtMost((configuration.screenWidthDp-32).dp))
                    .liquidGlass(RoundedCornerShape(24.dp),prominent=true,source=source)
                    .testTag("glass-quality-panel")
                    .heightIn(max=(configuration.screenHeightDp-64).dp.coerceAtMost(420.dp))
                    .verticalScroll(rememberScrollState()).padding(6.dp)) {
                    options.forEach { value ->
                        key(value) {
                            GlassQualitySurface(onClick={if(expanded) { choose(value);expanded=false }},
                                modifier=Modifier.testTag("glass-quality-$value").semantics { this.selected=value==selected;role=Role.RadioButton },
                                material=false) {
                                RadioButton(value==selected,onClick=null)
                                Spacer(Modifier.width(8.dp))
                                Text(if(value==0) "Best quality (up to 4K)" else "${value}p",
                                    modifier=Modifier.weight(1f),fontWeight=if(value==selected) FontWeight.SemiBold else FontWeight.Normal)
                                if(value==selected) Icon(Icons.Default.Check,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
    }
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

@Composable private fun GlassQualitySurface(onClick:()->Unit,modifier:Modifier=Modifier,
    material:Boolean=true,content:@Composable RowScope.()->Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressure=animateFloatAsState(if(pressed) 1f else 0f,spring(.65f,500f),label="Quality glass pressure")
    Surface(onClick=onClick,interactionSource=interaction,color=Color.Transparent,
        shape=RoundedCornerShape(18.dp),modifier=modifier.fillMaxWidth().heightIn(min=54.dp)
            .graphicsLayer { scaleX=1f-pressure.value*.018f;scaleY=1f+pressure.value*.015f }
            .then(if(material) Modifier.liquidGlass(RoundedCornerShape(18.dp),prominent=true,interaction={pressure.value}) else Modifier)) {
        Row(Modifier.padding(horizontal=10.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,content=content)
    }
}
