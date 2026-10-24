/* Button material/interaction adaptation of Square LiquidButton and the Backdrop
 * catalog (Apache-2.0), pinned originals and notices in minimal/vendor/square/.
 * Local implementation: fixed hit target, observing/cancelling taps without
 * consuming scroll moves, direct render-phase deformation and one return spring.
 */
package com.fuhbe.ytdlpg.simple

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.*

internal class ElasticButtonStats {
    var compositions=0;var sourceRecords=0;var readbacks=0;var returnSprings=0;var meshDraws=0
    var motion:ElasticButtonMotion?=null
    var captureUnavailable=false
}
private class ButtonPointerFlags { var physical=false;var keyboard=false }
private class ButtonSampleCache {
    var recorded:Any?=null;var sampled:Any?=null;var current:Any?=null
    var bitmap by mutableStateOf<Bitmap?>(null)
    var rejected by mutableStateOf(false)
}
internal val LocalElasticButtonStats=staticCompositionLocalOf<ElasticButtonStats?> { null }
/** Only render/graphics reads observe these floats. MOVE never launches a job. */
internal class ElasticButtonMotion(private val scope:CoroutineScope,private val stats:ElasticButtonStats?) {
    var level by mutableFloatStateOf(0f);private set
    var delta by mutableStateOf(Offset.Zero);private set
    var light by mutableStateOf(Offset.Zero);private set
    var touching=false;private set
    private var returning:Job?=null
    private val decay=Animatable(0f)
    private val settle=spring<Float>(.62f,480f,.001f)
    fun press(point:Offset) { returning?.cancel();touching=true;delta=Offset.Zero;light=point;level=1f }
    fun move(point:Offset,origin:Offset) { if(touching && point.x.isFinite() && point.y.isFinite()) { delta=point-origin;light=point } }
    fun release() {
        if(!touching)return
        touching=false;returning?.cancel();stats?.let { it.returnSprings++ }
        val start=level
        returning=scope.launch { decay.snapTo(start);decay.animateTo(0f,settle) { level=value.coerceIn(0f,1.1f) };level=0f }
    }
}
/** A rounded body whose long/short edges develop a bounded local bulge. It is
 * painted within a fixed hit target; labels and semantics are never transformed. */
internal fun elasticButtonPath(size:Size,level:Float,delta:Offset,light:Offset,path:Path,overscan:Float=0f):Path {
    path.reset();if(!size.width.isFinite() || !size.height.isFinite() || size.width<=0 || size.height<=0)return path
    val p=if(level.isFinite()) level.coerceIn(0f,1.1f) else 0f;val inset=if(overscan>0f)overscan else (size.minDimension*.06f).coerceAtLeast(.5f)
    val dx=(tanh(delta.x/(size.width*.22f+1f))*inset*.95f*p).toFloat()
    val dy=(tanh(delta.y/(size.height*.65f+1f))*inset*.95f*p).toFloat()
    val l=inset+inset*.08f*p;val r=size.width-inset-inset*.08f*p
    val t=inset+size.height*.018f*p;val b=size.height-inset-size.height*.018f*p
    val radius=((b-t)*.44f).coerceAtMost((r-l)*.5f)
    val cx=light.x.coerceIn(l+radius,r-radius)
    // One continuous outline, with edge curvature rather than glyph scaling.
    path.moveTo(l+radius,t)
    path.cubicTo(cx,t+min(0f,dy),cx,t+min(0f,dy),r-radius,t)
    path.quadraticTo(r+max(0f,dx),t,r+max(0f,dx),t+radius)
    path.cubicTo(r+max(0f,dx),t+radius,r+max(0f,dx),b-radius,r,b-radius)
    path.quadraticTo(r+max(0f,dx),b,r-radius,b)
    path.cubicTo(cx,b+max(0f,dy),cx,b+max(0f,dy),l+radius,b)
    path.quadraticTo(l+min(0f,dx),b,l+min(0f,dx),b-radius)
    path.cubicTo(l+min(0f,dx),b-radius,l+min(0f,dx),t+radius,l,t+radius)
    path.quadraticTo(l+min(0f,dx),t,l+radius,t);path.close()
    return path
}

@Composable internal fun GlassButton(
    onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,emphasized:Boolean=false,
    contentPadding:PaddingValues=PaddingValues(horizontal=12.dp,vertical=8.dp),
    content:@Composable RowScope.()->Unit
) {
    val action by rememberUpdatedState(onClick)
    val stats=LocalElasticButtonStats.current
    SideEffect { stats?.let { it.compositions++ } }
    val scope=rememberCoroutineScope();val motion=remember(scope) { ElasticButtonMotion(scope,stats) }
    SideEffect { stats?.motion=motion }
    val overscan=with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    val interaction=remember { MutableInteractionSource() }
    val pointers=remember { ButtonPointerFlags() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event -> when(event) {
            is PressInteraction.Press -> if(!pointers.physical) { pointers.keyboard=true;motion.press(Offset.Zero) }
            is PressInteraction.Release,is PressInteraction.Cancel -> if(pointers.keyboard) { pointers.keyboard=false;motion.release() }
            else -> Unit
        } }
    }
    val rendering=LocalGlassRendering.current
    val mesh=rendering!=GlassRendering.TINT && android.os.Build.VERSION.SDK_INT in 31..32
    val backgroundLayer=rememberGraphicsLayer();val requests=remember { Channel<Unit>(Channel.CONFLATED) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val dark=LocalGlassDark.current
    val revision=remember(bounds,dark,enabled,emphasized,rendering) { Any() }
    val cache=remember { ButtonSampleCache() }
    SideEffect { cache.current=revision }
    val lens=remember { GlassMeshLens() }
    LaunchedEffect(mesh,backgroundLayer) {
        for(event in requests) {
            if(!mesh || cache.rejected || cache.recorded==null || cache.sampled===cache.current)continue
            try {
                // Once per background revision/press, never per MOVE or spring frame.
                if(backgroundLayer.size.width.toLong()*backgroundLayer.size.height>2_000_000L)continue
                val token=cache.recorded
                val snapshot=backgroundLayer.toImageBitmap()
                val bitmap=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    snapshot.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
                }
                if(token===cache.recorded && token===cache.current && bitmap!=null) {
                    cache.bitmap=bitmap;cache.sampled=token;stats?.let { it.readbacks++ }
                }
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(_:RuntimeException) { cache.rejected=true;stats?.captureUnavailable=true;android.util.Log.w("YtdlpG-Glass","Button mesh capture unavailable; compatible material retained") }
            catch(_:LinkageError) { cache.rejected=true;stats?.captureUnavailable=true;android.util.Log.w("YtdlpG-Glass","Button mesh unsupported; compatible material retained") }
            catch(_:OutOfMemoryError) { cache.rejected=true;stats?.captureUnavailable=true;android.util.Log.w("YtdlpG-Glass","Button mesh memory unavailable; compatible material retained") }
        }
    }
    val observer=if(enabled) Modifier.pointerInput(motion,enabled) {
        awaitEachGesture {
            val down=awaitFirstDown(false,pass=PointerEventPass.Initial)
            pointers.physical=true;pointers.keyboard=false;motion.press(down.position)
            if(mesh)requests.trySend(Unit)
            var invalid=false
            try {
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    if(event.changes.any { it.id!=down.id && it.pressed }) { invalid=true;motion.release() }
                    val point=change.position
                    if((point-down.position).getDistance()>viewConfiguration.touchSlop)invalid=true
                    if(change.changedToUpIgnoreConsumed()) {
                        if(invalid || point.x<0 || point.y<0 || point.x>size.width || point.y>size.height)change.consume()
                        motion.release()
                        if(event.changes.none { it.pressed })break
                    } else if(change.pressed)motion.move(point,down.position)
                    else break
                    // Observe ancestor scroll arbitration. Never consume MOVE.
                    val last=awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id==down.id }
                    if(last!=null && last.pressed && last.position!=last.previousPosition && last.isConsumed) {
                        invalid=true;motion.release()
                    }
                    if(invalid && event.changes.none { it.pressed })break
                }
            } finally { pointers.physical=false;motion.release() }
        }
    } else Modifier
    val path=remember { Path() }
    val shade=if(emphasized) Color(0xFF1479E8).copy(alpha=if(dark) .26f else .18f) else Color.Transparent
    Box(modifier.heightIn(min=48.dp).then(observer)
        .onGloballyPositioned { val p=it.positionInWindow();val r=Rect(p,it.size.toSize());if(bounds!=r)bounds=r }
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,role=Role.Button) { action() },
        contentAlignment=Alignment.Center) {
        Box(Modifier.matchParentSize().layout { measurable,constraints ->
            val child=measurable.measure(Constraints.fixed(constraints.maxWidth+overscan*2,constraints.maxHeight+overscan*2))
            layout(constraints.maxWidth,constraints.maxHeight) { child.place(-overscan,-overscan) }
        }.graphicsLayer { alpha=if(enabled) 1f else .55f }.drawWithContent {
            val outline=elasticButtonPath(size,motion.level,motion.delta,motion.light+Offset(overscan.toFloat(),overscan.toFloat()),path,overscan.toFloat())
            clipPath(outline) { this@drawWithContent.drawContent() }
            drawPath(outline,Brush.radialGradient(listOf(Color.White.copy(alpha=.85f),Color.White.copy(alpha=.10f),Color.White.copy(alpha=.42f)),
                center=if(motion.level>.01f) motion.light+Offset(overscan.toFloat(),overscan.toFloat()) else Offset(size.width*.25f,0f),radius=size.width*.65f+size.height),
                style=Stroke((.85f+motion.level*.5f).dp.toPx()))
        }.drawWithContent {
            if(mesh && !cache.rejected) {
                if(cache.recorded!==revision && !motion.touching) {
                    backgroundLayer.record { this@drawWithContent.drawContent() };cache.recorded=revision;cache.bitmap=null
                    stats?.let { it.sourceRecords++ }
                }
                if(cache.recorded===revision)drawLayer(backgroundLayer) else drawContent()
                val bitmap=cache.bitmap
                if(bitmap!=null && cache.sampled===revision && motion.level>.001f) {
                    try { lens.draw(this,bitmap,Rect(Offset.Zero,size),motion.level,1f);stats?.let { it.meshDraws++ } }
                    catch(_:RuntimeException) { cache.rejected=true }
                }
            } else drawContent()
            drawRect(shade)
            if(motion.level>.001f)drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=.13f*motion.level),Color.Transparent),
                center=motion.light+Offset(overscan.toFloat(),overscan.toFloat()),radius=size.minDimension*2f))
        }.liquidGlass(RoundedCornerShape(24.dp),prominent=true))
        Row(Modifier.padding(contentPadding),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.Center,content=content)
    }
}

@Composable internal fun GlassIconButton(onClick:()->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,content:@Composable ()->Unit) {
    GlassButton(onClick,modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),enabled,
        contentPadding=PaddingValues(8.dp)) { content() }
}
