// Test-only exact 0.6.5 algorithm; package/class renamed for before-after comparison.
// Vendored from the Backdrop catalog app, Apache-2.0.
// https://github.com/Kyant0/AndroidLiquidGlass
// commit b18eb0ff12c616546a68c72e7d0097f1ab286c87
// Via Square 17ed2b87974cf6fec9c531ef3f1438448bb2b729.
// Local adaptations: normalized physical coordinates, monotonic clock, one
// cancellable job per channel, distinct gesture cancellation, no release waiter.
// Original source and license: minimal/vendor/square/. See PROVENANCE.md.
package com.fuhbe.ytdlpg.simple

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class LegacyGlassMotionFixture(private val animationScope: CoroutineScope, initialValue: Float) {
    // The five independent spring channels are Square's LegacyGlassMotionFixture.
    private val valueAnimationSpec = spring(1f, 1000f, .001f)
    private val velocityAnimationSpec = spring(.5f, 300f, .01f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, .001f)
    private val scaleXAnimationSpec = spring(.6f, 250f, .001f)
    private val scaleYAnimationSpec = spring(.7f, 250f, .001f)
    private val valueAnimation = Animatable(initialValue, .001f)
    private val velocityAnimation = Animatable(0f, .01f)
    private val pressProgressAnimation = Animatable(0f, .001f)
    private val scaleXAnimation = Animatable(1f, .001f)
    private val scaleYAnimation = Animatable(1f, .001f)
    private val velocityTracker = VelocityTracker()
    private var positionJob: Job? = null
    private var velocityJob: Job? = null
    private var pressureJob: Job? = null
    val value get() = valueAnimation.value
    val velocity get() = velocityAnimation.value
    val pressProgress get() = pressProgressAnimation.value
    val scaleX get() = scaleXAnimation.value
    val scaleY get() = scaleYAnimation.value

    fun press() {
        velocityTracker.resetTracking()
        pressureJob?.cancel()
        pressureJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            // Keep the larger Square catalog scale inside the compact app's track.
            launch { scaleXAnimation.animateTo(1.055f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1.065f, scaleYAnimationSpec) }
        }
    }
    fun release() {
        pressureJob?.cancel()
        velocityJob?.cancel()
        pressureJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYAnimationSpec) }
            launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
        }
    }
    fun updateValue(value: Float) {
        if (!value.isFinite()) return
        positionJob?.cancel()
        positionJob = animationScope.launch {
            valueAnimation.animateTo(value.coerceIn(0f, 1f), valueAnimationSpec) {
                velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(this.value, 0f))
                val targetVelocity = velocityTracker.calculateVelocity().x.coerceIn(-12f, 12f)
                velocityJob?.cancel()
                velocityJob = animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
            }
        }
    }
    fun animateToValue(value: Float) {
        updateValue(value)
        release()
    }
}
