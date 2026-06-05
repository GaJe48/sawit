package com.gaje48.lms.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun FloatingBlobsBackground(content: @Composable BoxScope.() -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(18000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "time",
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier =
                Modifier
                    .size(350.dp)
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = (40f * kotlin.math.sin(time)).dp.toPx() - 100.dp.toPx()
                        translationY = (40f * kotlin.math.cos(time)).dp.toPx() - 100.dp.toPx()
                        alpha = 0.4f
                    }.background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )

        Box(
            modifier =
                Modifier
                    .size(380.dp)
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        translationX = (50f * kotlin.math.cos(time + 1.5f)).dp.toPx() + 100.dp.toPx()
                        translationY = (50f * kotlin.math.sin(time + 1.5f)).dp.toPx() + 100.dp.toPx()
                        alpha = 0.35f
                    }.background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )

        Box(
            modifier =
                Modifier
                    .size(300.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = (60f * kotlin.math.sin(time + 3.0f)).dp.toPx()
                        translationY = (60f * kotlin.math.cos(time + 3.0f)).dp.toPx()
                        alpha = 0.25f
                    }.background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )

        content()
    }
}
