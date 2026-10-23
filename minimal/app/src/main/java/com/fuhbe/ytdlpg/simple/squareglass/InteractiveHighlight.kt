// Adapted from the Backdrop catalog InteractiveHighlight, Apache-2.0,
// via Square 17ed2b87974cf6fec9c531ef3f1438448bb2b729.
// Local changes: shared gesture stream, radial-gradient equivalent of
// Square's radial-light shader (also works below API 33), cancellable channels.
// Native refraction/specular AGSL remains in SquareGlassMaterial, independently.
package com.fuhbe.ytdlpg.simple.squareglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class InteractiveHighlight(private val animationScope: CoroutineScope) {
    private val pressProgressAnimationSpec = spring(.5f, 300f, .001f)
    private val positionAnimationSpec = spring(.5f, 300f, Offset.VisibilityThreshold)
    private val pressProgressAnimation = Animatable(0f, .001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero
    private var pressJob: Job? = null
    private var positionJob: Job? = null
    val pressProgress get() = pressProgressAnimation.value
    private var displayedPosition by mutableStateOf(Offset.Zero)
    val position get() = displayedPosition
    fun begin(position: Offset) {
        startPosition = position
        pressJob?.cancel()
        pressJob = animationScope.launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
        move(position)
    }
    fun move(position: Offset) {
        positionJob?.cancel()
        displayedPosition=position
    }
    fun end() {
        pressJob?.cancel(); positionJob?.cancel()
        pressJob = animationScope.launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
        positionJob = animationScope.launch { positionAnimation.snapTo(displayedPosition); positionAnimation.animateTo(startPosition, positionAnimationSpec) { displayedPosition=value } }
    }
    /** Must be painted after the opaque optical texture, not underneath it. */
    fun drawOverlay(scope:DrawScope,localPosition:Offset) = with(scope) {
        val progress=pressProgress.coerceIn(0f,1f)
        if(progress>.001f) {
            drawRect(Color.White.copy(alpha=.035f*progress),blendMode=BlendMode.Plus)
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=.07f*progress),Color.Transparent),
                center=localPosition,radius=size.minDimension*1.5f),blendMode=BlendMode.Plus)
        }
    }
    fun modifier(localPosition: () -> Offset): Modifier = Modifier.drawWithContent {
        drawOverlay(this,localPosition())
        drawContent()
    }
}
