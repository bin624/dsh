package com.bin624.dsh.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.bin624.dsh.ui.components.liquid.DampedDragAnimation
import com.bin624.dsh.ui.components.liquid.InteractiveHighlight
import com.bin624.dsh.ui.components.liquid.LiquidBottomTab
import com.bin624.dsh.ui.components.liquid.LocalLiquidBottomTabScale
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private data class NavigationItem(
    val title: String,
    val glyph: NavigationGlyph
)

private enum class NavigationGlyph {
    HOME,
    WALLET,
    KEY,
    SETTINGS
}

@Composable
fun LiquidGlassBottomBar(
    backdrop: Backdrop,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF087FF5) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.43f)
    } else {
        Color(0xFF171717).copy(alpha = 0.45f)
    }

    val items = listOf(
        NavigationItem("概览", NavigationGlyph.HOME),
        NavigationItem("明细", NavigationGlyph.WALLET),
        NavigationItem("密钥", NavigationGlyph.KEY),
        NavigationItem("设置", NavigationGlyph.SETTINGS)
    )
    val tabsBackdrop = rememberLayerBackdrop()
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    var currentIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(items.indices)) }
    LaunchedEffect(selectedIndex) {
        currentIndex = selectedIndex.coerceIn(items.indices)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / items.size
        }
        val offsetAnimation = remember { androidx.compose.animation.core.Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).coerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * androidx.compose.animation.core.EaseOut.transform(abs(fraction))
                }
            }
        }

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.coerceIn(items.indices).toFloat(),
                valueRange = 0f..(items.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.34f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, items.lastIndex)
                    if (targetIndex == currentIndex) {
                        animateToValue(targetIndex.toFloat())
                    } else {
                        currentIndex = targetIndex
                    }
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            androidx.compose.animation.core.spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (items.size - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onSelect(index)
                }
        }

        val interactiveHighlight = remember(animationScope, dampedDragAnimation, tabWidth) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) {
                            (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        } else {
                            size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        },
                        size.height / 2f
                    )
                }
            )
        }

        val tabsContent: @Composable RowScope.() -> Unit = {
            items.forEachIndexed { index, item ->
                val selected = index == currentIndex
                LiquidBottomTab(
                    onClick = {
                        if (index == currentIndex) {
                            dampedDragAnimation.animateToValue(index.toFloat())
                        } else {
                            currentIndex = index
                        }
                    }
                ) {
                    NavigationIcon(
                        glyph = item.glyph,
                        tint = if (selected) accentColor else Color(0xFF454955)
                    )
                    androidx.compose.material.Text(
                        text = item.title,
                        color = if (selected) accentColor else Color(0xFF454955),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }

        // The fixed vessel remains vertically centered and does not travel with
        // the active lens. Only its backdrop scale responds gently to pressure.
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 12.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(68.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = tabsContent
        )

        // Capture a duplicate tab layer. The active lens samples both this tab
        // layer and the page behind the bar, so labels and glyphs refract through it.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.20f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(60.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent
            )
        }

        // Centered 56dp moving glass lens. The containing 76dp bar leaves equal
        // vertical breathing room above and below, unlike the previous top-aligned pill.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        dampedDragAnimation.value * tabWidth + panelOffset
                    } else {
                        size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                    },
                    shadow = {
                        Shadow(alpha = dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * dampedDragAnimation.pressProgress,
                            alpha = dampedDragAnimation.pressProgress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = 0.10f)
                            else Color.White.copy(alpha = 0.10f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / items.size)
        )
    }
}

@Composable
private fun NavigationIcon(
    glyph: NavigationGlyph,
    tint: Color
) {
    androidx.compose.foundation.Canvas(Modifier.size(25.dp)) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.9.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        when (glyph) {
            NavigationGlyph.HOME -> {
                val roof = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.12f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.48f)
                }
                drawPath(roof, tint, style = stroke)
                drawRoundRect(
                    tint,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.43f),
                    size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.46f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
                    style = stroke
                )
                drawLine(
                    tint,
                    androidx.compose.ui.geometry.Offset(w * 0.47f, h * 0.89f),
                    androidx.compose.ui.geometry.Offset(w * 0.47f, h * 0.63f),
                    strokeWidth = stroke.width
                )
            }
            NavigationGlyph.WALLET -> {
                drawRoundRect(
                    tint,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.10f, h * 0.20f),
                    size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.13f),
                    style = stroke
                )
                drawLine(
                    tint,
                    androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.40f),
                    androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.40f),
                    strokeWidth = stroke.width
                )
                drawCircle(
                    tint,
                    radius = w * 0.055f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.61f)
                )
            }
            NavigationGlyph.KEY -> {
                drawCircle(
                    tint,
                    radius = w * 0.23f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.33f, h * 0.37f),
                    style = stroke
                )
                drawLine(
                    tint,
                    androidx.compose.ui.geometry.Offset(w * 0.49f, h * 0.53f),
                    androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.90f),
                    strokeWidth = stroke.width
                )
                drawLine(
                    tint,
                    androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.72f),
                    androidx.compose.ui.geometry.Offset(w * 0.81f, h * 0.59f),
                    strokeWidth = stroke.width
                )
            }
            NavigationGlyph.SETTINGS -> {
                drawCircle(
                    tint,
                    radius = w * 0.34f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.50f),
                    style = stroke
                )
                drawCircle(
                    tint,
                    radius = w * 0.11f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.50f),
                    style = stroke
                )
                for (index in 0 until 8) {
                    val angle = Math.PI * index / 4.0
                    val innerRadius = w * 0.36f
                    val outerRadius = w * 0.46f
                    val x1 = w * 0.50f + kotlin.math.cos(angle).toFloat() * innerRadius
                    val y1 = h * 0.50f + kotlin.math.sin(angle).toFloat() * innerRadius
                    val x2 = w * 0.50f + kotlin.math.cos(angle).toFloat() * outerRadius
                    val y2 = h * 0.50f + kotlin.math.sin(angle).toFloat() * outerRadius
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(x1, y1),
                        androidx.compose.ui.geometry.Offset(x2, y2),
                        strokeWidth = stroke.width
                    )
                }
            }
        }
    }
}
