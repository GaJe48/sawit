package com.gaje48.lms.ui.screens.attendance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaje48.lms.model.AttendanceScreenData
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

@Preview
@Composable
fun PreviewAttendanceScreen() {
    AttendanceScreenStateless(
        courseName = "Pemrograman Visual Lanjut",
        attendanceScreenDatas =
            listOf(
                AttendanceScreenData(true, listOf("https://example.com")),
                AttendanceScreenData(false, listOf("https://example.com")),
                AttendanceScreenData(false, emptyList()),
            ),
        isProcessingAttendance = false,
        isLoading = false,
        isRefreshing = false,
        errorMessage = null,
        onRefresh = {},
        onRetry = {},
        onAttendClick = {},
        onBackClick = {},
    )
}

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AttendanceScreenStateless(
        courseName = uiState.courseName ?: return,
        attendanceScreenDatas = uiState.attendanceScreenDatas,
        isProcessingAttendance = uiState.isProcessingAttendance,
        isLoading = uiState.isLoading,
        isRefreshing = uiState.isRefreshing,
        errorMessage = uiState.errorMessage,
        onRefresh = { viewModel.getAttendances(UpdateAction.REFRESH) },
        onRetry = { viewModel.getAttendances() },
        onAttendClick = { viewModel.processAttendance(it) },
        onBackClick = onBackClick,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeMaterialsApi::class,
)
@Composable
fun AttendanceScreenStateless(
    courseName: String,
    attendanceScreenDatas: List<AttendanceScreenData>,
    isProcessingAttendance: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAttendClick: (List<String>) -> Unit,
    onBackClick: () -> Unit,
) {
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()

    val infiniteTransition = rememberInfiniteTransition(label = "attendance_blobs")
    val blobTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(30000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "blob_time",
    )

    if (isProcessingAttendance) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            modifier =
                Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp)),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingGif(label = "Sedang memproses presensi Anda...")
                }
            },
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier =
                Modifier
                    .size(450.dp)
                    .graphicsLayer {
                        translationX = (20f * kotlin.math.sin(blobTime)).dp.toPx() - 100.dp.toPx()
                        translationY = (20f * kotlin.math.cos(blobTime)).dp.toPx() - 100.dp.toPx()
                    }.background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .size(350.dp)
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        translationX = (30f * kotlin.math.cos(blobTime + 1f)).dp.toPx() - 100.dp.toPx()
                        translationY = (30f * kotlin.math.sin(blobTime + 1f)).dp.toPx() + 100.dp.toPx()
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
            isRefreshing = isRefreshing,
            state = pullToRefreshState,
            onRefresh = onRefresh,
            contentAlignment = Alignment.TopCenter,
            indicator = {
                SyncIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
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
                        title = {
                            Column {
                                Text(
                                    text = "Rekap Presensi",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    text = courseName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    )
                },
            ) { paddingValues ->
                if (attendanceScreenDatas.isEmpty()) {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .hazeSource(hazeState),
                        contentPadding =
                            PaddingValues(
                                top = paddingValues.calculateTopPadding() + 16.dp,
                                bottom = 24.dp,
                                start = 20.dp,
                                end = 20.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
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
                                            onRetry = onRetry,
                                        )
                                    else -> EmptyGif(label = "Belum ada data absen")
                                }
                            }
                        }
                    }
                } else {
                    val attendedCount = attendanceScreenDatas.count { it.isAttended }
                    val totalCount = attendanceScreenDatas.size
                    val percent = if (totalCount > 0) (attendedCount * 100 / totalCount) else 0

                    LazyVerticalGrid(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .hazeSource(hazeState),
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding =
                            PaddingValues(
                                top = paddingValues.calculateTopPadding() + 16.dp,
                                bottom = 32.dp,
                                start = 20.dp,
                                end = 20.dp,
                            ),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    ),
                                border =
                                    BorderStroke(
                                        1.dp,
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                                            ),
                                        ),
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Tingkat Kehadiran",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$attendedCount dari $totalCount Pertemuan Hadir",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(72.dp),
                                    ) {
                                        val animatedSweep = remember { Animatable(0f) }
                                        LaunchedEffect(percent) {
                                            animatedSweep.animateTo(
                                                targetValue = percent * 3.6f,
                                                animationSpec = tween(1000, easing = FastOutSlowInEasing),
                                            )
                                        }

                                        val primaryColor = MaterialTheme.colorScheme.primary
                                        val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawArc(
                                                color = trackColor,
                                                startAngle = -90f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                                            )
                                            drawArc(
                                                color = primaryColor,
                                                startAngle = -90f,
                                                sweepAngle = animatedSweep.value,
                                                useCenter = false,
                                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                                            )
                                        }

                                        Text(
                                            text = "$percent%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    ),
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
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                                    RoundedCornerShape(12.dp),
                                                ).padding(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Pemberitahuan",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Informasi Penting Presensi",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Sistem mendeteksi kehadiran secara otomatis ketika Anda mengunduh materi. Namun, harap ikuti instruksi dosen jika presensi dilakukan lewat formulir eksternal atau tugas khusus.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 20.sp,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "⚠️ PERINGATAN: Menekan tombol \"Isi Absen\" berulang kali secara cepat dapat membebani server dan mengakibatkan pemblokiran akun otomatis.",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.error,
                                            letterSpacing = 0.2.sp,
                                        )
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Riwayat Sesi Kuliah",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        itemsIndexed(attendanceScreenDatas) { index, attendanceScreenData ->
                            AttendanceCard(
                                attendanceIndex = index + 1,
                                attendanceScreenData = attendanceScreenData,
                                onAttendClick = {
                                    onAttendClick(attendanceScreenData.contentUrls)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceCard(
    attendanceIndex: Int,
    attendanceScreenData: AttendanceScreenData,
    onAttendClick: () -> Unit,
) {
    val isAttended = attendanceScreenData.isAttended
    val accentColor = if (isAttended) Color(0xFF10B981) else Color(0xFFEF4444)
    val containerColor =
        if (isAttended) {
            MaterialTheme.colorScheme.primaryContainer.copy(
                alpha = 0.2f,
            )
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "btn_scale")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, accentColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SESI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = accentColor.copy(alpha = 0.8f),
                    letterSpacing = 1.5.sp,
                )

                Icon(
                    imageVector = if (isAttended) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accentColor,
                )
            }

            Text(
                text = "%02d".format(attendanceIndex),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = accentColor,
            )

            if (isAttended) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Hadir Tercatat",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                    )
                }
            } else if (attendanceScreenData.contentUrls.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = buttonScale
                                scaleY = buttonScale
                            }.clip(RoundedCornerShape(10.dp))
                            .background(accentColor)
                            .clickable(interactionSource = interactionSource, indication = null) {
                                onAttendClick()
                            }.height(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Isi Absen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Tidak Ada Link",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
