package com.gaje48.lms.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val showIndicator = state.distanceFraction > 0f || isRefreshing || state.isAnimating

    if (showIndicator) {
        val isExpanded = isRefreshing || (state.isAnimating && state.distanceFraction > 0.1f)

        val width by animateDpAsState(
            targetValue = if (isExpanded) 220.dp else 40.dp,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            label = "indicator_width",
        )

        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        val targetTranslationY = (statusBarHeight + 8.dp - 56.dp).coerceAtMost(0.dp)

        val translationYOffset by animateDpAsState(
            targetValue = if (isExpanded) targetTranslationY else 0.dp,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "indicator_translation_y",
        )

        PullToRefreshDefaults.IndicatorBox(
            state = state,
            isRefreshing = isRefreshing,
            modifier =
                modifier
                    .graphicsLayer {
                        translationY = translationYOffset.toPx()
                    }.width(width)
                    .height(40.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            elevation = 8.dp,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(24.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = isExpanded,
                    animationSpec = tween(durationMillis = 250),
                    label = "indicator_content",
                ) { expanded ->
                    if (expanded) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Menyinkronkan data...",
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                maxLines = 1,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(22.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val startFraction = 0.4f
                            val progress =
                                if (state.distanceFraction < startFraction) {
                                    0f
                                } else {
                                    ((state.distanceFraction - startFraction) / (1f - startFraction)).coerceIn(0f, 1f)
                                }

                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            )
                        }
                    }
                }
            }
        }
    }
}
