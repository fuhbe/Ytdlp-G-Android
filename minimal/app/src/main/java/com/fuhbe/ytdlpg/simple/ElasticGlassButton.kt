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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.fuhbe.ytdlpg.simple.buttonsquare.shapes.ContinuousCapsule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.withTransform
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
    var settled by mutableStateOf(true);private set
    private var pressPoint=Offset.Zero
    private var returning:Job?=null
    private val decay=Animatable(0f)
    private val settle=spring<Float>(.5f,300f,.001f)
    fun press(point:Offset) { returning?.cancel();touching=true;settled=false;pressPoint=point;delta=Offset.Zero;light=point;level=1f }
    fun move(point:Offset,origin:Offset) { if(touching && point.x.isFinite() && point.y.isFinite()) { delta=point-origin;light=point } }
    fun release() {
        if(!touching)return
        touching=false;returning?.cancel();stats?.let { it.returnSprings++ }
        val start=level;val pull=delta
        returning=scope.launch { decay.snapTo(1f);decay.animateTo(0f,settle) { level=(start*value).coerceIn(0f,1.1f);delta=pull*value;light=pressPoint+delta };level=0f;delta=Offset.Zero;settled=true }
    }
}
@Composable internal fun GlassButton(
    onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,emphasized:Boolean=false,
    contentPadding:PaddingValues=PaddingValues(horizontal=12.dp,vertical=8.dp),
    content:@Composable RowScope.()->Unit
) {
    val action by rememberUpdatedState(onClick)
    val stats=LocalElasticButtonStats.current
    SideEffect { stats?.let { it.compositions++ } }
    val host=LocalGlassButtonHost.current
    val localScope=rememberCoroutineScope()
    // Input tracking can outlive a LazyColumn item while the finger is down.
    // Return cleanup belongs to the root scope; the body is drawn only in-place.
    val scope=host?.animationScope ?: localScope
    val motion=remember(scope) { ElasticButtonMotion(scope,stats) }
    SideEffect { stats?.motion=motion }
    val entry=remember(motion) { GlassButtonEntry(motion) }
    SideEffect { entry.enabled=enabled }
    DisposableEffect(host,entry) { host?.register(entry);onDispose { host?.unregister(entry) } }
    LaunchedEffect(enabled) { if(!enabled)motion.release() }
    val density=LocalDensity.current.density
    val interaction=remember { MutableInteractionSource() }
    val pointers=remember { ButtonPointerFlags() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event -> when(event) {
            is PressInteraction.Press -> if(!pointers.physical && host?.ownsWindow(entry)!=true) { pointers.keyboard=true;motion.press(Offset.Zero) }
            is PressInteraction.Release,is PressInteraction.Cancel -> if(pointers.keyboard) { pointers.keyboard=false;if(host?.ownsWindow(entry)!=true)motion.release() }
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
            pointers.physical=true;pointers.keyboard=false
            if(host?.ownsWindow(entry)!=true && host?.rejectAdditionalPointers!=true) {
                host?.beginLocal(entry,down.position)
                motion.press(down.position)
            }
            if(mesh)requests.trySend(Unit)
            var invalid=host?.rejectAdditionalPointers==true
            try {
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    if(event.changes.any { it.id!=down.id && it.pressed }) { invalid=true;if(host?.ownsWindow(entry)!=true)motion.release() }
                    val point=change.position
                    if((point-down.position).getDistance()>viewConfiguration.touchSlop)invalid=true
                    if(change.changedToUpIgnoreConsumed()) {
                        if(invalid || host?.rejectAdditionalPointers==true || point.x<0 || point.y<0 || point.x>size.width || point.y>size.height)change.consume()
                        if(host?.ownsWindow(entry)!=true)motion.release()
                        if(event.changes.none { it.pressed })break
                    } else if(change.pressed) { if(host?.ownsWindow(entry)!=true && host?.rejectAdditionalPointers!=true)motion.move(point,down.position) }
                    else break
                    // Observe ancestor scroll arbitration. Never consume MOVE.
                    val last=awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id==down.id }
                    if(last!=null && last.pressed && last.position!=last.previousPosition && last.isConsumed) {
                        invalid=true // Scroll invalidates a click, not the visual stream.
                    }
                    if(invalid && event.changes.none { it.pressed })break
                }
            } finally { pointers.physical=false;if(host?.ownsWindow(entry)!=true)motion.release() }
        }
    } else Modifier
    val shape=remember { ContinuousCapsule() }
    Box(modifier.heightIn(min=48.dp).then(observer)
        .onGloballyPositioned { val p=it.positionInWindow();val r=Rect(p,it.size.toSize());if(bounds!=r)bounds=r;entry.bounds=r }
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,role=Role.Button) { if(!entry.blockPointerClick)action() },
        contentAlignment=Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha=if(enabled)1f else .55f }.drawWithContent {
            val transform=squareButtonTransform(size,motion.delta,motion.level,density)
            withTransform({ translate(transform.translation.x,transform.translation.y);scale(transform.scaleX,transform.scaleY,center) }) {
                this@drawWithContent.drawContent()
            }
        }.clip(shape).drawWithContent {
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
            if(motion.level>.001f)drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=.13f*motion.level),Color.Transparent),
                center=motion.light,radius=size.minDimension*2f))
        }.squareButtonMaterial(motion,emphasized,entry.optics))
        Row(Modifier.padding(contentPadding),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.Center,content=content)
    }
}

@Composable internal fun GlassIconButton(onClick:()->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,content:@Composable ()->Unit) {
    GlassButton(onClick,modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),enabled,
        contentPadding=PaddingValues(8.dp)) { content() }
}
