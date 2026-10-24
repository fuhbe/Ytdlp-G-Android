/* Ytdlp-G UI-only input/return-lifetime host for Square-derived buttons.
 * Observes the existing Activity dispatch stream; delegates every event unchanged.
 * No root paint clone and no recognizer above the four approved selectors.
 */
package com.fuhbe.ytdlpg.simple

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import android.view.Window
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.first
import java.lang.ref.WeakReference

internal class GlassButtonEntry(val motion:ElasticButtonMotion) {
    var bounds=Rect.Zero
    var origin=Rect.Zero
    var enabled=true
    var attached=true
    var blockPointerClick=false
    val optics=SquareButtonOptics()
}
internal class GlassButtonHostState(val animationScope:kotlinx.coroutines.CoroutineScope?=null) {
    private val entries=linkedSetOf<GlassButtonEntry>()
    var active by mutableStateOf<GlassButtonEntry?>(null);private set
    var root=Offset.Zero
    private var windowStream=false
    private var pointer=-1
    private var start=Offset.Zero
    private var invalid=false
    var rejectAdditionalPointers=false;private set
    var slop=8f
    fun register(entry:GlassButtonEntry) { entry.attached=true;entries+=entry }
    fun unregister(entry:GlassButtonEntry) { entry.attached=false;entries-=entry /* active body survives lazy disposal until physical UP */ }
    fun ownsWindow(entry:GlassButtonEntry)=active===entry && windowStream
    fun beginLocal(entry:GlassButtonEntry,point:Offset) {
        if(windowStream)return
        active?.takeIf { it!==entry }?.motion?.release()
        entry.origin=entry.bounds;active=entry;start=point;invalid=false
    }
    fun before(event:MotionEvent,decorScreen:Offset) {
        fun point()=Offset(event.rawX-decorScreen.x,event.rawY-decorScreen.y)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rejectAdditionalPointers=false
                val p=point();val entry=entries.lastOrNull { it.enabled && it.bounds.contains(p) } ?: return
                active?.motion?.release();active=entry;entry.origin=entry.bounds
                windowStream=true;pointer=event.getPointerId(0);start=p;invalid=false;entry.blockPointerClick=false
                entry.motion.press(p-entry.origin.topLeft)
            }
            MotionEvent.ACTION_MOVE -> if(windowStream) {
                val entry=active ?: return
                if(event.pointerCount!=1 || event.findPointerIndex(pointer)<0) { invalid=true;entry.motion.release();return }
                val p=point();if((p-start).getDistance()>slop)invalid=true
                // Screen/window coordinates survive parent scrolling and leaving
                // the original child. Consumption by a scroller is not CANCEL.
                entry.motion.move(p-entry.origin.topLeft,start-entry.origin.topLeft)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                rejectAdditionalPointers=true
                if(windowStream) { invalid=true;active?.motion?.release() }
            }
            MotionEvent.ACTION_POINTER_UP -> if(rejectAdditionalPointers)entries.forEach { it.blockPointerClick=true }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                if(rejectAdditionalPointers)entries.forEach { it.blockPointerClick=true }
                if(windowStream) {
                val entry=active ?: return
                entry.blockPointerClick=invalid || event.actionMasked==MotionEvent.ACTION_CANCEL || !entry.bounds.contains(point())
                entry.motion.release()
                }
            }
        }
    }
    fun after(event:MotionEvent) {
        if(rejectAdditionalPointers)entries.forEach { it.blockPointerClick=false }
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) {
            active?.blockPointerClick=false;windowStream=false;pointer=-1;rejectAdditionalPointers=false
        }
    }
    fun finish(entry:GlassButtonEntry) { if(active===entry && entry.motion.settled)active=null }
    fun dispose() { active?.motion?.release();active=null;entries.clear();windowStream=false }
}
internal val LocalGlassButtonHost=staticCompositionLocalOf<GlassButtonHostState?> { null }
private fun Context.activity():Activity? { var c=this;repeat(12) { if(c is Activity)return c as Activity;if(c is ContextWrapper)c=(c as ContextWrapper).baseContext else return null };return null }

@Composable internal fun GlassButtonHost(content:@Composable ()->Unit) {
    val animationScope=rememberCoroutineScope()
    val host=remember(animationScope) { GlassButtonHostState(animationScope) };val view=LocalView.current;val context=LocalContext.current
    val window=remember(context) { context.activity()?.window }
    val slop=LocalViewConfiguration.current.touchSlop
    SideEffect { host.slop=slop }
    DisposableEffect(window,host) {
        val previous=window?.callback
        val weak=WeakReference(host)
        val xy=IntArray(2)
        val observer=if(previous!=null)object:Window.Callback by previous {
            override fun dispatchTouchEvent(event:MotionEvent):Boolean {
                val h=weak.get();window!!.decorView.getLocationOnScreen(xy)
                h?.before(event,Offset(xy[0].toFloat(),xy[1].toFloat()))
                return try { previous.dispatchTouchEvent(event) } finally { h?.after(event) }
            }
        } else null
        if(observer!=null)window?.callback=observer
        onDispose { if(window?.callback===observer)window?.callback=previous;host.dispose() }
    }
    CompositionLocalProvider(LocalGlassButtonHost provides host) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { host.root=it.positionInWindow() }) {
            content()
            val entry=host.active
            if(entry!=null)key(entry) {
                LaunchedEffect(entry) { snapshotFlow { entry.motion.settled }.first { it };host.finish(entry) }
                // Input ownership/return lifetime only. The actual button is
                // painted exactly once in-place; no cloned root body or label.
            }
        }
    }
}
