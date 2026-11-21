package com.bin624.dsh.ui.components.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock

/**
 * Damped, interruptible tab motion.
 *
 * Pointer samples are sent through conflated channels instead of creating a
 * coroutine for every move event. This prevents a backlog when devices dispatch
 * touch events faster than the render thread can process them.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec =
        spring<Float>(dampingRatio = 0.86f, stiffness = 390f, visibilityThreshold = visibilityThreshold)
    private val velocityAnimationSpec =
        spring<Float>(dampingRatio = 0.72f, stiffness = 320f, visibilityThreshold = visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        spring<Float>(dampingRatio = 0.72f, stiffness = 520f, visibilityThreshold = 0.001f)
    private val scaleXAnimationSpec =
        spring<Float>(dampingRatio = 0.64f, stiffness = 340f, visibilityThreshold = 0.001f)
    private val scaleYAnimationSpec =
        spring<Float>(dampingRatio = 0.70f, stiffness = 300f, visibilityThreshold = 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    private val valueUpdates = Channel<Float>(Channel.CONFLATED)
    private val velocityUpdates = Channel<Float>(Channel.CONFLATED)

    init {
        // Exactly one value-animation collector; new pointer samples replace
        // stale targets, so motion never queues up behind old drag events.
        animationScope.launch {
            valueUpdates.receiveAsFlow().collectLatest { target ->
                valueAnimation.animateTo(target, valueAnimationSpec) {
                    updateVelocity()
                }
            }
        }
        animationScope.launch {
            velocityUpdates.receiveAsFlow().collect { target ->
                velocityAnimation.snapTo(target)
            }
        }
    }

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        valueUpdates.trySend(value.coerceIn(valueRange))
    }

    fun animateToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                valueUpdates.trySend(target)
                if (velocity != 0f) velocityUpdates.trySend(0f)
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            Clock.System.now().toEpochMilliseconds(),
            Offset(value, 0f)
        )
        val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
        val targetVelocity = velocityTracker.calculateVelocity().x / range
        velocityUpdates.trySend(targetVelocity)
    }
}
