package com.bin624.dsh.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.Text
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
    val items = listOf(
        NavigationItem("概览", NavigationGlyph.HOME),
        NavigationItem("明细", NavigationGlyph.WALLET),
        NavigationItem("密钥", NavigationGlyph.KEY),
        NavigationItem("设置", NavigationGlyph.SETTINGS)
    )
    val safeSelectedIndex = selectedIndex.coerceIn(items.indices)
    val scope = rememberCoroutineScope()
    val tabBackdrop = rememberLayerBackdrop()
    val indicatorProgress = remember { Animatable(safeSelectedIndex.toFloat()) }
    val pressProgress = remember { Animatable(0f) }
    val dragVelocity = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val motionSpring = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 420f,
        visibilityThreshold = 0.001f
    )
    val pressSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 620f,
        visibilityThreshold = 0.001f
    )

    LaunchedEffect(safeSelectedIndex) {
        if (!isDragging) {
            indicatorProgress.animateTo(safeSelectedIndex.toFloat(), motionSpring)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .pointerInput(items.size) {
                val insetPx = 6.dp.toPx()
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        scope.launch { pressProgress.animateTo(1f, pressSpring) }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val itemWidthPx = (size.width - insetPx * 2f) / items.size
                        val fingerProgress = (
                            (change.position.x - insetPx - itemWidthPx / 2f) / itemWidthPx
                        ).coerceIn(0f, (items.size - 1).toFloat())
                        scope.launch { indicatorProgress.snapTo(fingerProgress) }
                        val normalizedVelocity = (dragAmount.x / itemWidthPx) * 1.9f
                        scope.launch { dragVelocity.snapTo(normalizedVelocity) }
                    },
                    onDragEnd = {
                        val target = kotlin.math.round(indicatorProgress.value)
                            .toInt()
                            .coerceIn(items.indices)
                        isDragging = false
                        onSelect(target)
                        scope.launch {
                            launch { indicatorProgress.animateTo(target.toFloat(), motionSpring) }
                            launch { pressProgress.animateTo(0f, pressSpring) }
                            launch {
                                dragVelocity.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.58f, stiffness = 300f)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        val target = safeSelectedIndex
                        isDragging = false
                        scope.launch {
                            launch { indicatorProgress.animateTo(target.toFloat(), motionSpring) }
                            launch { pressProgress.animateTo(0f, pressSpring) }
                            launch {
                                dragVelocity.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.58f, stiffness = 300f)
                                )
                            }
                        }
                    }
                )
            }
    ) {
        val innerHorizontalPadding = 6.dp
        val itemWidth = (maxWidth - innerHorizontalPadding * 2) / items.size
        val itemWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            itemWidth.toPx()
        }
        val insetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            innerHorizontalPadding.toPx()
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(22.dp.toPx(), 26.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.72f) },
                    shadow = { Shadow(alpha = 0.16f) },
                    innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.12f) },
                    onDrawSurface = {
                        drawRect(Color(0xDFFFFFFF))
                    }
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.96f),
                            Color(0xFFE7E5EC).copy(alpha = 0.78f)
                        )
                    ),
                    shape = Capsule()
                )
        )

        // Real tab labels and glyphs are captured into a separate backdrop layer.
        // The moving lens combines this layer with the page backdrop, so nearby
        // icons/text visibly refract through the selected glass instead of being
        // hidden by a flat selected-state fill.
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = innerHorizontalPadding, vertical = 5.dp)
                .layerBackdrop(tabBackdrop),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == safeSelectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Capsule())
                        .clickable(
                            role = Role.Tab,
                            onClick = {
                                onSelect(index)
                                scope.launch {
                                    indicatorProgress.animateTo(index.toFloat(), motionSpring)
                                }
                                scope.launch {
                                    pressProgress.animateTo(0.48f, pressSpring)
                                    delay(90)
                                    pressProgress.animateTo(0f, pressSpring)
                                }
                            }
                        )
                        .semantics {
                            contentDescription = item.title
                            role = Role.Tab
                            selected = isSelected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NavigationIcon(
                        glyph = item.glyph,
                        tint = if (isSelected) Color(0xFF087FF5) else Color(0xFF454955)
                    )
                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF087FF5) else Color(0xFF454955)
                    )
                }
            }
        }

        // A true moving lens: it samples both the screen behind the bar and the
        // actual tab artwork, then distorts it with chromatic aberration.
        Box(
            Modifier
                .padding(horizontal = innerHorizontalPadding)
                .graphicsLayer {
                    translationX = indicatorProgress.value * itemWidthPx
                    val pressure = pressProgress.value
                    val velocity = abs(dragVelocity.value).coerceIn(0f, 0.42f)
                    scaleX = 1f + velocity * 0.42f + pressure * 0.025f
                    scaleY = 1f + pressure * 0.11f - velocity * 0.08f
                }
                .width(itemWidth)
                .height(66.dp)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val pressure = pressProgress.value
                        lens(
                            8.dp.toPx() + 5.dp.toPx() * pressure,
                            12.dp.toPx() + 7.dp.toPx() * pressure,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = 0.62f + pressProgress.value * 0.34f)
                    },
                    shadow = { Shadow(alpha = 0.10f + pressProgress.value * 0.14f) },
                    innerShadow = {
                        InnerShadow(
                            radius = 6.dp + 3.dp * pressProgress.value,
                            alpha = 0.12f + pressProgress.value * 0.12f
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.055f + pressProgress.value * 0.055f))
                        drawRect(Color(0xFF087FF5).copy(alpha = 0.018f))
                    }
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.92f),
                            Color(0xFFB8D9FF).copy(alpha = 0.42f),
                            Color(0xFFF8E6F6).copy(alpha = 0.52f),
                            Color.White.copy(alpha = 0.84f)
                        )
                    ),
                    shape = Capsule()
                )
        )

    }
}

@Composable
private fun NavigationIcon(
    glyph: NavigationGlyph,
    tint: Color
) {
    Canvas(Modifier.size(25.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = 1.9.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (glyph) {
            NavigationGlyph.HOME -> {
                val roof = Path().apply {
                    moveTo(w * 0.12f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.48f)
                }
                drawPath(roof, tint, style = stroke)
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.22f, h * 0.43f),
                    size = Size(w * 0.56f, h * 0.46f),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = stroke
                )
                drawLine(
                    tint,
                    Offset(w * 0.47f, h * 0.89f),
                    Offset(w * 0.47f, h * 0.63f),
                    strokeWidth = stroke.width
                )
            }
            NavigationGlyph.WALLET -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.10f, h * 0.20f),
                    size = Size(w * 0.80f, h * 0.62f),
                    cornerRadius = CornerRadius(w * 0.13f),
                    style = stroke
                )
                drawLine(
                    tint,
                    Offset(w * 0.12f, h * 0.40f),
                    Offset(w * 0.88f, h * 0.40f),
                    strokeWidth = stroke.width
                )
                drawCircle(tint, radius = w * 0.055f, center = Offset(w * 0.70f, h * 0.61f))
            }
            NavigationGlyph.KEY -> {
                drawCircle(
                    tint,
                    radius = w * 0.23f,
                    center = Offset(w * 0.33f, h * 0.37f),
                    style = stroke
                )
                drawLine(
                    tint,
                    Offset(w * 0.49f, h * 0.53f),
                    Offset(w * 0.86f, h * 0.90f),
                    strokeWidth = stroke.width
                )
                drawLine(
                    tint,
                    Offset(w * 0.68f, h * 0.72f),
                    Offset(w * 0.81f, h * 0.59f),
                    strokeWidth = stroke.width
                )
            }
            NavigationGlyph.SETTINGS -> {
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                for (index in 0 until 8) {
                    val angle = Math.PI * index / 4.0
                    val innerRadius = w * 0.36f
                    val outerRadius = w * 0.46f
                    val x1 = w * 0.50f + cos(angle).toFloat() * innerRadius
                    val y1 = h * 0.50f + sin(angle).toFloat() * innerRadius
                    val x2 = w * 0.50f + cos(angle).toFloat() * outerRadius
                    val y2 = h * 0.50f + sin(angle).toFloat() * outerRadius
                    drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width)
                }
            }
        }
    }
}
