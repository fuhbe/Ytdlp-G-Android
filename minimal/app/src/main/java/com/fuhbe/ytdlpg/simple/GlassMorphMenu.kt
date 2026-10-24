/* New transition geometry on the pinned Square/Backdrop material integration.
 * The original Square/catalog source and GPL-3.0/Apache-2.0 notices remain in
 * minimal/vendor/square/. Arbitrary necks use blur/rim; the pinned AGSL lens
 * supports CornerBasedShape only, so the neck is not presented as an AGSL lens.
 */
package com.fuhbe.ytdlpg.simple

import android.graphics.Rect as AndroidRect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.kyant.backdrop.Backdrop

internal class GlassMorphMenuState {
    var mounted by mutableStateOf(false);private set
    var expanded by mutableStateOf(false);private set
    var handoff by mutableStateOf(false);private set
    fun handoff() { handoff=true }
    val progress=Animatable(0f)
    val resolver=MetaballResolver()
    fun toggle() { if(!mounted) { mounted=true;expanded=true } else expanded=!expanded }
    fun close() { expanded=false }
    fun unmount() { if(!expanded) { mounted=false;handoff=false } }
}
internal class GlassMorphStats {
    var compositions=0;var frames=0
    var state:GlassMorphMenuState?=null
    var placement:GlassMorphPlacement?=null
    var source:GlassOverlaySource?=null
    var scene:MetaballScene?=null
    var windowOrigin=Offset.Zero
}
internal val LocalGlassMorphStats=staticCompositionLocalOf<GlassMorphStats?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GlassMorphMenu(
    state:GlassMorphMenuState,anchor:Rect?,options:List<Int>,selected:Int,
    choose:(Int)->Unit,trigger:@Composable RowScope.()->Unit
) {
    val stats=LocalGlassMorphStats.current
    SideEffect { stats?.let { it.compositions++;it.state=state } }
    val density=LocalDensity.current;val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val source=LocalGlassOverlaySource.current
    val wallpaper=LocalGlassBackdrop.current
    val rendering=LocalGlassRendering.current
    var rootScreen by remember { mutableStateOf<Offset?>(null) }
    var viewBounds by remember { mutableStateOf(Rect.Zero) }
    val origin=if(anchor!=null && rootScreen!=null)anchor.translate(-rootScreen!!) else null
    val placement=remember(origin,viewBounds,options.size,density,rtl) {
        origin?.let { glassMorphPlacement(it,viewBounds,options.size,density.density,density.fontScale,rtl) }
    }
    val ready=placement!=null
    val interactive by remember(state) { derivedStateOf { state.expanded && !state.progress.isRunning && state.progress.value>=.999f } }
    val currentChoose by rememberUpdatedState(choose)
    val currentOptions by rememberUpdatedState(options)
    DisposableEffect(source,state.mounted,state.handoff,ready) {
        val acquired=state.mounted && state.handoff && ready && source!=null
        if(acquired) { source.users++;source.frozenUsers++;source.ready=false }
        onDispose { if(acquired) { source.users=(source.users-1).coerceAtLeast(0);source.frozenUsers=(source.frozenUsers-1).coerceAtLeast(0) } }
    }
    val measured=origin!=null && viewBounds.width>0 && viewBounds.height>0
    LaunchedEffect(state.expanded,state.mounted,ready,measured,options.isEmpty()) {
        if(!state.mounted)return@LaunchedEffect
        if(!ready) {
            if(state.handoff || !state.expanded || measured || options.isEmpty()) { state.close();state.progress.snapTo(0f);state.unmount() }
            return@LaunchedEffect
        }
        state.handoff()
        val velocity=state.progress.velocity
        state.progress.animateTo(if(state.expanded)1f else 0f,
            spring(if(state.expanded)LiquidMorphPhysics.OPEN_DAMPING_RATIO else LiquidMorphPhysics.CLOSE_DAMPING_RATIO,
                if(state.expanded)LiquidMorphPhysics.OPEN_STIFFNESS else LiquidMorphPhysics.CLOSE_STIFFNESS,.001f),
            initialVelocity=if(!state.expanded && kotlin.math.abs(velocity)<.05f)LiquidMorphPhysics.CLOSE_VELOCITY else velocity)
        if(!state.expanded)state.unmount()
    }
    val focus=remember { FocusRequester() }
    LaunchedEffect(interactive,selected,options) { if(interactive && options.isNotEmpty())runCatching { focus.requestFocus() } }
    if(!state.mounted)return
    Popup(popupPositionProvider=remember { object:PopupPositionProvider { override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize)=IntOffset.Zero } },
        onDismissRequest=state::close,properties=PopupProperties(focusable=true,usePlatformDefaultWidth=false,clippingEnabled=true)) {
        val view=LocalView.current
        Box(Modifier.fillMaxSize().testTag("glass-morph-window").onGloballyPositioned { coords ->
            val location=coords.localToScreen(Offset.Zero)
            if(location.x.isFinite() && location.y.isFinite() && rootScreen!=location)rootScreen=location
            stats?.windowOrigin=location
            val frame=AndroidRect();view.getWindowVisibleDisplayFrame(frame)
            val full=Rect(Offset.Zero,coords.size.toSize())
            val visible=if(frame.width()>0 && frame.height()>0)Rect(frame.left-location.x,frame.top-location.y,frame.right-location.x,frame.bottom-location.y).intersect(full) else full
            if(viewBounds!=visible)viewBounds=visible
        },contentAlignment=AbsoluteAlignment.TopLeft) {
            Box(Modifier.matchParentSize().testTag("glass-quality-dismiss-region")
                .clickable(interactionSource=remember { androidx.compose.foundation.interaction.MutableInteractionSource() },indication=null) { state.close() })
            if(placement!=null) {
                SideEffect { stats?.let { it.placement=placement;it.source=source } }
                val backdrop=if(source?.ready==true)source.backdrop else wallpaper
                val o=placement.origin;val panel=placement.panel
                val union=Rect(kotlin.math.min(o.left,panel.left),kotlin.math.min(o.top,panel.top),kotlin.math.max(o.right,panel.right),kotlin.math.max(o.bottom,panel.bottom))
                    .inflate(with(density){56.dp.toPx()}).intersect(placement.viewport)
                fun Modifier.at(rect:Rect)=absoluteOffset { IntOffset(rect.left.toInt(),rect.top.toInt()) }
                    .size(with(density){rect.width.toDp()},with(density){rect.height.toDp()})
                val frame=remember(placement,state,density) { {
                    state.resolver.resolve(placement,state.progress.value,density.density,!state.expanded).also { stats?.scene=it }
                } }
                MetaballGlassSurface(Modifier.at(union),union,backdrop,frame)
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    Box(Modifier.at(o).testTag("glass-morph-origin").graphicsLayer {
                        val f=frame();translationX=f.a.rect.center.x-o.center.x;translationY=f.a.rect.center.y-o.center.y
                        alpha=(1f-state.progress.value.coerceIn(0f,1f)/.16f).coerceIn(0f,1f)
                    }) {
                        GlassQualitySurface(onClick=state::toggle,modifier=Modifier.testTag("glass-quality-trigger"),material=false,content=trigger)
                    }
                    val block=if(!interactive)Modifier.pointerInput(Unit) { awaitEachGesture {
                        awaitFirstDown(false).consume()
                        do { val e=awaitPointerEvent();e.changes.forEach { it.consume() } }while(e.changes.any { it.pressed })
                    } } else Modifier
                    Column(Modifier.at(panel).testTag("glass-quality-panel").then(block)
                        .graphicsLayer { alpha=glassMorphEase((state.progress.value-.86f)/.14f) }
                        .drawWithContent {
                            val scene=frame()
                            val r=scene.b.rect.translate(-panel.topLeft)
                            val clip=Path().apply { addRoundRect(RoundRect(r,scene.b.radius,scene.b.radius)) }
                            clipPath(clip) { this@drawWithContent.drawContent() }
                        }
                        .then(if(!interactive)Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier)
                        .verticalScroll(rememberScrollState()).padding(6.dp)) {
                        options.forEach { value -> key(value) {
                            val request=if(value==selected || (selected !in options && value==options.firstOrNull()))Modifier.focusRequester(focus) else Modifier
                            GlassQualitySurface(onClick={ if(interactive && value in currentOptions) { currentChoose(value);state.close() } },
                                enabled=interactive,modifier=Modifier.testTag("glass-quality-$value").then(request).semantics { this.selected=value==selected;role=Role.RadioButton },material=false) {
                                RadioButton(value==selected,onClick=null,modifier=Modifier.size(22.dp));Spacer(Modifier.width(8.dp))
                                Text(if(value==0)uiText("Best quality") else "${value}p",modifier=Modifier.weight(1f))
                                if(value==selected)Icon(androidx.compose.material.icons.Icons.Default.Check,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                            }
                        } }
                    }
                }
            }
        }
    }
}
