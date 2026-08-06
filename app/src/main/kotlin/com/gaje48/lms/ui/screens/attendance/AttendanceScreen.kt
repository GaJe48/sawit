package com.gaje48.lms.ui.screens.attendance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.gaje48.lms.R
import com.gaje48.lms.model.AttendanceScreenData
import com.gaje48.lms.ui.components.EmptyGif
import com.gaje48.lms.ui.components.FloatingBlobsBackground
import com.gaje48.lms.ui.components.LoadingGif
import com.gaje48.lms.ui.components.rememberPressedState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        onAttendClick = { },
        onBackClick = { },
    )
}

@Composable
fun AttendanceScreen(component: AttendanceComponent) {
    val uiState by component.uiState.subscribeAsState()

    AttendanceScreenStateless(
        courseName = uiState.courseName ?: return,
        attendanceScreenDatas = uiState.attendanceScreenDatas,
        isProcessingAttendance = uiState.isProcessingAttendance,
        onAttendClick = { component.processAttendance(it) },
        onBackClick = { component.onBackClick() },
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
    onAttendClick: (List<String>) -> Unit,
    onBackClick: () -> Unit,
) {
    FloatingBlobsBackground {
        val scope = rememberCoroutineScope()

        val hazeState = rememberHazeState()

        if (isProcessingAttendance) {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                shape = RoundedCornerShape(24.dp),
                modifier =
                    Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                            shape = RoundedCornerShape(24.dp),
                        ),
                containerColor = MaterialTheme.colorScheme.surface.copy(0.8f),
                text = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingGif(label = stringResource(R.string.attendance_processing_dialog))
                    }
                },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                    modifier = Modifier.hazeEffect(hazeState, HazeMaterials.ultraThin()),
                    navigationIcon = {
                        val backInteraction = remember { MutableInteractionSource() }
                        val isBackPressed by rememberPressedState(backInteraction)
                        val backScale by animateFloatAsState(
                            targetValue = if (isBackPressed) 0.95f else 1f,
                            label = "back_scale",
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    delay(150)
                                    onBackClick()
                                }
                            },
                            interactionSource = backInteraction,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f)),
                            contentPadding = PaddingValues(),
                            modifier =
                                Modifier.padding(start = 12.dp, end = 4.dp).size(50.dp).graphicsLayer {
                                    scaleX = backScale
                                    scaleY = backScale
                                },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.meeting_back_desc))
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.attendance_recap_title),
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
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) { paddingValues ->

            if (attendanceScreenDatas.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.hazeSource(hazeState),
                    contentPadding =
                        PaddingValues(
                            start = 20.dp,
                            top = paddingValues.calculateTopPadding() + 16.dp,
                            end = 20.dp,
                            bottom = 20.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyGif(label = stringResource(R.string.attendance_no_attendances))
                        }
                    }
                }

                return@Scaffold
            }

            LazyVerticalGrid(
                modifier = Modifier.hazeSource(hazeState),
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding =
                    PaddingValues(
                        start = 20.dp,
                        top = paddingValues.calculateTopPadding() + 16.dp,
                        end = 20.dp,
                        bottom = 20.dp,
                    ),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer.copy(0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val attendedCount = attendanceScreenDatas.count { it.isAttended }
                            val totalCount = attendanceScreenDatas.size
                            val percent = if (totalCount > 0) (attendedCount * 100 / totalCount) else 0

                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.attendance_rate_title),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.attendance_count_formatted, attendedCount, totalCount),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }

                            Box(Modifier.size(72.dp), Alignment.Center) {
                                val animatedSweep = remember { Animatable(0f) }
                                LaunchedEffect(percent) {
                                    animatedSweep.animateTo(
                                        targetValue = percent * 3.6f,
                                        animationSpec = tween(1000, easing = FastOutSlowInEasing),
                                    )
                                }

                                val primaryColor = MaterialTheme.colorScheme.primary
                                val trackColor = MaterialTheme.colorScheme.primary.copy(0.2f)

                                Canvas(Modifier.fillMaxSize()) {
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
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface.copy(0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(0.2f),
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = stringResource(R.string.attendance_info_title),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.attendance_info_title),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.attendance_info_desc),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp,
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = stringResource(R.string.attendance_info_warning),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.2.sp,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.attendance_history_title),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                itemsIndexed(items = attendanceScreenDatas, key = { index, _ -> index }) { index, attendanceScreenData ->
                    AttendanceCard(
                        attendanceIndex = index + 1,
                        attendanceScreenData = attendanceScreenData,
                        onAttendClick = { onAttendClick(attendanceScreenData.contentUrls) },
                    )
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
    val scope = rememberCoroutineScope()

    val isAttended = attendanceScreenData.isAttended

    val accentColor = if (isAttended) Color(0xFF059669) else Color(0xFFEF4444)
    val containerColor =
        if (isAttended) {
            MaterialTheme.colorScheme.primaryContainer.copy(0.2f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(0.2f)
        }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor),
        border = BorderStroke(1.dp, accentColor.copy(0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.attendance_session_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
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
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.attendance_present_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                    )
                }
            } else if (attendanceScreenData.contentUrls.isNotEmpty()) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by rememberPressedState(interactionSource)
                val buttonScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    label = "btn_scale",
                )

                Button(
                    onClick = {
                        scope.launch {
                            delay(150)
                            onAttendClick()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(accentColor),
                    modifier =
                        Modifier.fillMaxWidth().height(38.dp).graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        },
                    interactionSource = interactionSource,
                ) {
                    Text(
                        text = stringResource(R.string.attendance_button_attend),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.attendance_no_link_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
