package com.gaje48.lms.ui.screens.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaje48.lms.R
import com.gaje48.lms.model.UpdateAction
import com.gaje48.lms.ui.components.EmptyGif
import com.gaje48.lms.ui.components.ErrorGif
import com.gaje48.lms.ui.components.LoadingGif
import com.gaje48.lms.ui.components.SyncIndicator
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeMaterialsApi::class,
)
@Composable
fun ContentScreen(
    viewModel: ContentViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val contents = uiState.contents
    val isLoading = uiState.isLoading
    val errorMessage = uiState.errorMessage

    val uriHandler = LocalUriHandler.current
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val state = rememberPullToRefreshState()

    val infiniteTransition = rememberInfiniteTransition(label = "content_blobs")
    val blobTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(28000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "blob_time",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier =
                Modifier
                    .size(400.dp)
                    .graphicsLayer {
                        translationX = (25f * kotlin.math.sin(blobTime)).dp.toPx() - 100.dp.toPx()
                        translationY = (25f * kotlin.math.cos(blobTime)).dp.toPx() - 50.dp.toPx()
                    }.background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .size(350.dp)
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        translationX = (30f * kotlin.math.cos(blobTime + 1.5f)).dp.toPx() + 80.dp.toPx()
                        translationY = (30f * kotlin.math.sin(blobTime + 1.5f)).dp.toPx() + 80.dp.toPx()
                    }.background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            state = state,
            onRefresh = { viewModel.fetchContents(UpdateAction.REFRESH) },
            contentAlignment = Alignment.TopCenter,
            indicator = {
                SyncIndicator(
                    state = state,
                    isRefreshing = uiState.isRefreshing,
                )
            },
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        scrollBehavior = scrollBehavior,
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = HazeMaterials.ultraThin(),
                            ),
                        title = {
                            Text(
                                text = "Materi & Tautan",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBackClick,
                                modifier =
                                    Modifier
                                        .padding(start = 12.dp, end = 4.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { paddingValues ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState),
                    contentPadding =
                        PaddingValues(
                            top = paddingValues.calculateTopPadding() + 16.dp,
                            bottom = 32.dp,
                            start = 20.dp,
                            end = 20.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (contents.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    isLoading -> LoadingGif()
                                    errorMessage != null ->
                                        ErrorGif(
                                            message = errorMessage,
                                            onRetry = { viewModel.fetchContents() },
                                        )
                                    else -> EmptyGif(label = "Tidak ada file materi yang tersedia")
                                }
                            }
                        }
                    } else {
                        val fileKeywords = listOf("pdf", "word", "powerpoint", "excel", "archive")

                        val (files, links) =
                            contents.partition { item ->
                                fileKeywords.any { keyword ->
                                    item.type.contains(keyword, ignoreCase = true)
                                }
                            }

                        if (files.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    icon = Icons.Default.Description,
                                    label = "File Pembelajaran",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            items(files) { item ->
                                ContentCard(
                                    title = item.title,
                                    description = "Ketuk untuk mengunduh berkas",
                                    icon = iconPainter(item.type),
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    onClick = { viewModel.downloadFile(item.contentUrl) },
                                )
                            }
                        }

                        if (links.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                SectionHeader(
                                    icon = Icons.Default.Link,
                                    label = "Tautan Pendukung",
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            items(links) { item ->
                                ContentCard(
                                    title = item.title,
                                    description = "Ketuk untuk membuka link di browser",
                                    icon = iconPainter(item.type),
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    onClick = { uriHandler.openUri(item.contentUrl) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    icon: ImageVector,
    label: String,
    tint: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = tint,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun ContentCard(
    title: String,
    description: String,
    icon: Painter,
    accentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "card_scale")

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                },
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border =
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f),
                    ),
                ),
            ),
        interactionSource = interactionSource,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier =
                    Modifier
                        .size(52.dp)
                        .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = accentColor,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun iconPainter(type: String): Painter =
    when {
        type.contains("pdf") -> painterResource(id = R.drawable.pdf)
        type.contains("powerpoint") -> painterResource(id = R.drawable.ppt)
        type.contains("picture") -> painterResource(id = R.drawable.image)
        type.contains("video") -> painterResource(id = R.drawable.video)
        else -> painterResource(id = R.drawable.link)
    }
