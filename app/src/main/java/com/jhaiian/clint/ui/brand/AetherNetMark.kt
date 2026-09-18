package com.jhaiian.clint.ui.brand

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jhaiian.clint.R

data class AetherNetPalette(val ink: Color, val signal: Color, val knockout: Color) {
    companion object {
        val Light = AetherNetPalette(Color(0xFF0A0A0D), Color(0xFFE11D2E), Color(0xFFF7F7F8))
        val Dark = AetherNetPalette(Color(0xFFF7F7F8), Color(0xFFFF3346), Color(0xFF0A0A0D))
    }
}

val SoraSemiBold = FontFamily(Font(R.font.sora_semibold, FontWeight.SemiBold))

// Geometry and motion from the AetherNet "C4 v3 · Motion" design, in its 200x200 viewBox.
private object MarkGeometry {
    private fun parse(d: String, fillType: PathFillType = PathFillType.NonZero): Path =
        PathParser().parsePathString(d).toPath().apply { this.fillType = fillType }

    val ring = parse("M16 100 A84 84 0 1 0 184 100 A84 84 0 1 0 16 100 Z M23 100 A77 77 0 1 0 177 100 A77 77 0 1 0 23 100 Z", PathFillType.EvenOdd)
    val clip = parse("M22 100 A78 78 0 1 0 178 100 A78 78 0 1 0 22 100 Z")
    val head = parse("M100 64 C121 64 134 79 134 98 C134 117 120 133 100 138 C80 133 66 117 66 98 C66 79 79 64 100 64 Z")
    val wing = parse("M128 82 L186 56 L150 90 Z M129 93 L162 100 L145 103 Z")
    val visor = parse("M72 91 L128 86 L126 104 L74 107 Z")
    val eyes = parse("M80 96 L96 100 L95 103 L81 102 Z M104 100 L120 95 L119 101 L105 103 Z")
    val linesY = floatArrayOf(42f, 58f, 79f, 100f, 121f, 142f, 158f)
}

private val CssEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Keyframes 0% -> 50% -> 100% (a, b, a), each half eased, like a CSS `infinite` cycle. */
private fun pingPong(timeMs: Long, periodMs: Long, a: Float, b: Float, phase: Float = 0f): Float {
    val p = ((timeMs.toFloat() / periodMs + phase) % 1f + 1f) % 1f
    return if (p < 0.5f) lerp(a, b, CssEaseInOut.transform(p * 2f)) else lerp(b, a, CssEaseInOut.transform((p - 0.5f) * 2f))
}

private fun blinkScale(timeMs: Long): Float {
    val p = (timeMs % 4200L).toFloat() / 4200f
    return when {
        p < 0.90f -> 1f
        p < 0.93f -> lerp(1f, 0.1f, CssEase.transform((p - 0.90f) / 0.03f))
        p < 0.96f -> lerp(0.1f, 1f, CssEase.transform((p - 0.93f) / 0.03f))
        else -> 1f
    }
}

private fun flutterDegrees(timeMs: Long): Float {
    // 0.9s ease-in-out, `alternate`: -5deg -> 6deg -> -5deg over 1.8s.
    val cycle = (timeMs % 1800L).toFloat() / 900f
    val t = if (cycle <= 1f) cycle else 2f - cycle
    return lerp(-5f, 6f, CssEaseInOut.transform(t))
}

@Composable
private fun rememberAnimationTimeMs(animated: Boolean): Long {
    val context = LocalContext.current
    val motionEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    var time by remember { mutableLongStateOf(0L) }
    if (animated && motionEnabled) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { time = it - start }
            }
        }
    }
    return time
}

@Composable
fun AetherNetMark(
    palette: AetherNetPalette,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    meridianPeriodMs: Long = 9000L
) {
    val time = rememberAnimationTimeMs(animated)
    Canvas(modifier) {
        val unit = size.minDimension / 200f
        translate((size.width - 200f * unit) / 2f, (size.height - 200f * unit) / 2f) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawMark(palette, time, meridianPeriodMs)
            }
        }
    }
}

private fun DrawScope.drawMark(palette: AetherNetPalette, time: Long, meridianPeriodMs: Long) {
    val g = MarkGeometry
    val thin = Stroke(width = 2.4f)
    val center = Offset(100f, 100f)

    clipPath(g.clip) {
        for (y in g.linesY) drawLine(palette.ink, Offset(20f, y), Offset(180f, y), strokeWidth = 2.4f)
        for (i in 0 until 4) {
            val sx = pingPong(time, meridianPeriodMs, 1f, -1f, phase = i * 0.125f)
            scale(scaleX = sx, scaleY = 1f, pivot = center) {
                drawCircle(palette.ink, radius = 78f, center = center, style = thin)
            }
        }
    }
    drawPath(g.ring, palette.ink)

    val bobY = pingPong(time, 3200L, 0f, -3f)
    val bobDeg = pingPong(time, 3200L, -2.5f, 2.5f)
    val knockoutStroke = Stroke(width = 10f, join = StrokeJoin.Miter)
    translate(0f, bobY) {
        rotate(bobDeg, pivot = center) {
            val flutter = flutterDegrees(time)
            val wingPivot = Offset(130f, 88f)
            drawPath(g.head, palette.knockout)
            drawPath(g.head, palette.knockout, style = knockoutStroke)
            rotate(flutter, pivot = wingPivot) {
                drawPath(g.wing, palette.knockout)
                drawPath(g.wing, palette.knockout, style = knockoutStroke)
            }
            drawPath(g.head, palette.signal)
            rotate(flutter, pivot = wingPivot) { drawPath(g.wing, palette.signal) }
            drawPath(g.visor, palette.knockout)
            scale(scaleX = 1f, scaleY = blinkScale(time), pivot = Offset(100f, 99f)) {
                drawPath(g.eyes, palette.signal)
            }
        }
    }
}
