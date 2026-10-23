package com.fuhbe.ytdlpg.simple

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pointer coordinates are local to the reserved dock, never the system gesture area. */
internal fun glassDockTarget(position: Offset, size: IntSize): Int? =
    if (size.width <= 0 || size.height <= 0 || !position.x.isFinite() || !position.y.isFinite() ||
        position.x < 0f || position.x >= size.width || position.y < 0f || position.y >= size.height) null
    else (position.x / (size.width / 3f)).toInt().coerceIn(0, 2)

/** One decorative capsule. No semantics/clicks and no business state. Android's
 * animation duration scale is respected by Compose's finite spring animation. */
@Composable private fun GlassCapsule(index: Float, count: Int, size: IntSize,
    moving: Boolean, modifier: Modifier = Modifier) {
    val position by animateFloatAsState(index, spring(dampingRatio = .86f, stiffness = 560f), label = "Glass position")
    val compression by animateFloatAsState(if (moving) .94f else 1f,
        spring(dampingRatio = .9f, stiffness = 700f), label = "Glass compression")
    val activity by animateFloatAsState(if (moving || abs(position-index) > .015f) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 450f), label = "Glass light")
    val density = LocalDensity.current
    if (size.width > 0) {
        val cell = size.width.toFloat() / count
        Box(Modifier.width(with(density) { cell.toDp() }).fillMaxHeight()
            .offset { IntOffset((position.coerceIn(0f, (count-1).toFloat()) * cell).roundToInt(), 0) }
            .then(modifier)
            .graphicsLayer { scaleY = compression; scaleX = 1f + (1f-compression)*.25f }
            .liquidGlass(RoundedCornerShape(25.dp), prominent = true, selected = true,
                interaction = { activity }, lightPosition = { (position / count + .2f).coerceIn(0f,1f) }))
    }
}

/** Same three destinations and exact callbacks as 0.6.3. Drag dispatches once,
 * only at a valid release, and never invokes the already selected destination. */
@Composable internal fun GlassNavigationDock(
    area: String, onBack: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit,
    source: Backdrop?, modifier: Modifier = Modifier
) {
    val selected = when(area) { "downloads" -> 1; "settings" -> 2; else -> 0 }
    val currentSelection by rememberUpdatedState(selected)
    val currentActions by rememberUpdatedState(listOf(onBack,onDownloads,onSettings))
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val trackPressure by animateFloatAsState(if(dragging) 1f else 0f,
        spring(dampingRatio=1f,stiffness=450f),label="Dock reflection")
    val target = if (dragging && size.width > 0) (pointer.x/(size.width/3f)-.5f).coerceIn(0f,2f) else selected.toFloat()
    val trackBackdrop=rememberLayerBackdrop()
    val sampled=LocalGlassRendering.current!=GlassRendering.TINT
    Box(modifier.testTag("glass-navigation")
        .liquidGlass(RoundedCornerShape(30.dp), prominent = true, source = source,
            exportedBackdrop=if(sampled) trackBackdrop else null,
            interaction = { trackPressure },
            lightPosition = { if(dragging && size.width>0) (pointer.x/size.width).coerceIn(0f,1f) else (selected+.5f)/3f })
        .padding(5.dp)) {
        Box(Modifier.fillMaxWidth().pointerInput(Unit) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=awaitHorizontalTouchSlopOrCancellation(down.id) { change,_ -> change.consume() }
                if(start != null) {
                    pointer=start.position;dragging=true
                    try {
                        while(true) {
                            val event=awaitPointerEvent()
                            val change=event.changes.firstOrNull { it.id==start.id } ?: break
                            pointer=change.position
                            if(change.isConsumed || event.changes.any { it.id!=start.id && it.pressed }) break
                            if(change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                val destination=glassDockTarget(pointer,size)
                                if(destination!=null && destination!=currentSelection) currentActions[destination]()
                                break
                            }
                            if(!change.pressed) break
                            change.consume()
                        }
                    } finally { dragging=false }
                }
            }
        }) {
            Box(Modifier.matchParentSize()) {
                CompositionLocalProvider(LocalGlassBackdrop provides if(sampled) trackBackdrop else source) {
                    GlassCapsule(target,3,size,dragging,Modifier.testTag("glass-navigation-capsule"))
                }
            }
            Row(Modifier.fillMaxWidth().onSizeChanged { size = it }, verticalAlignment = Alignment.CenterVertically) {
                DockDestination("Início","Voltar",Icons.Default.Home,selected==0,onBack,Modifier.weight(1f))
                DockDestination("Downloads","Downloads",Icons.Default.DownloadForOffline,selected==1,onDownloads,Modifier.weight(1f))
                DockDestination("Configurações","Configurações",Icons.Default.Settings,selected==2,onSettings,Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun DockDestination(label:String, description:String, icon:ImageVector,
    active:Boolean, action:()->Unit, modifier:Modifier) {
    val color = if(active) Color.White else MaterialTheme.colorScheme.onSurface
    val semantics = Modifier.semantics { selected=active; role=Role.Tab; contentDescription=description }
    val body:@Composable ()->Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal=3.dp,vertical=8.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Icon(icon,null,Modifier.size(22.dp),tint=color)
            Text(label,fontSize=10.sp,fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal,color=color)
        }
    }
    if(active) Box(modifier.heightIn(min=56.dp).then(semantics),contentAlignment=Alignment.Center) { body() }
    else Surface(onClick=action,color=Color.Transparent,shape=RoundedCornerShape(25.dp),
        modifier=modifier.heightIn(min=56.dp).then(semantics)) { body() }
}

/** Presentation for the existing segmented controls: same values and callbacks,
 * a single sliding decoration instead of separately painted selected cells. */
@Composable internal fun GlassSelectionTrack(selected:Int,count:Int,content:@Composable RowScope.()->Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(22.dp),prominent=true).padding(3.dp)) {
        Box(Modifier.matchParentSize()) { GlassCapsule(selected.toFloat(),count,size,false) }
        Row(Modifier.fillMaxWidth().onSizeChanged { size=it },content=content)
    }
}
