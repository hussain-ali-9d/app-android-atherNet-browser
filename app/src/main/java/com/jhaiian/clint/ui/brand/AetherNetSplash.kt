package com.jhaiian.clint.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SplashMarkSize = 180.dp
private val WordmarkGap = 20.dp
private val WordmarkLineHeight = 40.dp

@Composable
fun AetherNetWordmark(palette: AetherNetPalette, fontSizeSp: Float, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            append("Aether")
            withStyle(SpanStyle(color = palette.signal)) { append("Net") }
        },
        color = palette.ink,
        fontFamily = SoraSemiBold,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSizeSp.sp,
        letterSpacing = (-0.02).em,
        modifier = modifier
    )
}

/**
 * Launch overlay: picks up from the system splash (same mark, same size, centered),
 * lifts the mark to reveal the wordmark, then fades out.
 */
@Composable
fun AetherNetSplash(isDark: Boolean, onFinished: () -> Unit) {
    val palette = if (isDark) AetherNetPalette.Dark else AetherNetPalette.Light
    val reveal = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    val liftPx = with(LocalDensity.current) { ((WordmarkGap + WordmarkLineHeight) / 2).toPx() }

    LaunchedEffect(Unit) {
        delay(150)
        reveal.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        delay(900)
        exit.animateTo(0f, tween(350))
        onFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(palette.knockout)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WordmarkGap)
        ) {
            AetherNetMark(
                palette = palette,
                modifier = Modifier
                    .size(SplashMarkSize)
                    .graphicsLayer { translationY = liftPx * (1f - reveal.value) }
            )
            AetherNetWordmark(
                palette = palette,
                fontSizeSp = 34f,
                modifier = Modifier.graphicsLayer {
                    alpha = reveal.value
                    translationY = liftPx * 0.5f * (1f - reveal.value)
                }
            )
        }
    }
}
