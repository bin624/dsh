package com.bin624.dsh.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.StrokeCap
import androidx.compose.ui.graphics.drawscope.StrokeJoin
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
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        val itemWidth = (maxWidth - 12.dp) / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = 6.dp + itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
            label = "liquid-tab-position"
        )

        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(12.dp.toPx())
                        lens(24.dp.toPx(), 28.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow(alpha = 0.18f) },
                    innerShadow = { InnerShadow(radius = 7.dp, alpha = 0.10f) },
                    onDrawSurface = {
                        drawRect(Color(0xDDFEFCFD))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.86f), Capsule())
        )

        Box(
            Modifier
                .offset(x = indicatorOffset, y = 6.dp)
                .width(itemWidth)
                .height(64.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(5.dp.toPx())
                        lens(18.dp.toPx(), 22.dp.toPx(), chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.85f) },
                    shadow = { Shadow(alpha = 0.20f) },
                    innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.12f) },
                    onDrawSurface = {
                        drawRect(Color(0xDDEAF1FF))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.96f), Capsule())
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Capsule())
                        .clickable { onSelect(index) }
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
                        tint = if (isSelected) Color(0xFF087FF5) else Color(0xFF565A66)
                    )
                    Text(
                        text = item.title,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF087FF5) else Color(0xFF454955)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    glyph: NavigationGlyph,
    tint: Color
) {
    Canvas(Modifier.size(24.dp)) {
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
                drawLine(tint, Offset(w * 0.47f, h * 0.89f), Offset(w * 0.47f, h * 0.63f), strokeWidth = stroke.width)
            }
            NavigationGlyph.WALLET -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.10f, h * 0.20f),
                    size = Size(w * 0.80f, h * 0.62f),
                    cornerRadius = CornerRadius(w * 0.13f),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.12f, h * 0.40f), Offset(w * 0.88f, h * 0.40f), strokeWidth = stroke.width)
                drawCircle(tint, radius = w * 0.055f, center = Offset(w * 0.70f, h * 0.61f))
            }
            NavigationGlyph.KEY -> {
                drawCircle(
                    tint,
                    radius = w * 0.23f,
                    center = Offset(w * 0.33f, h * 0.37f),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.49f, h * 0.53f), Offset(w * 0.86f, h * 0.90f), strokeWidth = stroke.width)
                drawLine(tint, Offset(w * 0.68f, h * 0.72f), Offset(w * 0.81f, h * 0.59f), strokeWidth = stroke.width)
            }
            NavigationGlyph.SETTINGS -> {
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                for (index in 0 until 8) {
                    val angle = Math.PI * index / 4.0
                    val innerRadius = w * 0.36f
                    val outerRadius = w * 0.46f
                    val x1 = w * 0.50f + kotlin.math.cos(angle).toFloat() * innerRadius
                    val y1 = h * 0.50f + kotlin.math.sin(angle).toFloat() * innerRadius
                    val x2 = w * 0.50f + kotlin.math.cos(angle).toFloat() * outerRadius
                    val y2 = h * 0.50f + kotlin.math.sin(angle).toFloat() * outerRadius
                    drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width)
                }
            }
        }
    }
}
