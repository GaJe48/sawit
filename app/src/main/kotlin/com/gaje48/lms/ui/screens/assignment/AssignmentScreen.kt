package com.gaje48.lms.ui.screens.assignment

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaje48.lms.model.AssignmentScreenData
import com.gaje48.lms.ui.components.EmptyGif
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun AssignmentScreen(
    viewModel: AssignmentViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val courseName = uiState.courseName ?: return
    val assignmentScreenDatas = uiState.assignmentScreenDatas

    val uriHandler = LocalUriHandler.current
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var currentSubmitUrl by remember { mutableStateOf("") }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && currentSubmitUrl.isNotEmpty()) {
                viewModel.uploadSubmission(
                    uri,
                    currentSubmitUrl,
                )
            }
        }

    val infiniteTransition = rememberInfiniteTransition(label = "assignment_blobs")
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                        Column {
                            Text(
                                text = "Tugas Kuliah",
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
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier =
                                Modifier
                                    .padding(start = 12.dp, end = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        CircleShape,
                                    ).border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding =
                    PaddingValues(
                        top = paddingValues.calculateTopPadding() + 16.dp,
                        bottom = 32.dp,
                        start = 20.dp,
                        end = 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (assignmentScreenDatas.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { EmptyGif(label = "Belum ada tugas kuliah") }
                    }

                    return@LazyColumn
                }

                val mimeTypes =
                    arrayOf(
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-powerpoint",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/zip",
                        "application/x-7z-compressed",
                        "application/x-rar-compressed",
                    )

                items(assignmentScreenDatas) { assignmentScreenData ->
                    AssignmentCard(
                        assignmentScreenData = assignmentScreenData,
                        onDownloadClick = {
                            assignmentScreenData.assignmentFileUrl?.let(viewModel::downloadQuestion)
                        },
                        onViewClick = {
                            assignmentScreenData.submissionFileUrl?.let(uriHandler::openUri)
                        },
                        onSubmitClick = {
                            currentSubmitUrl = assignmentScreenData.assignmentUrl
                            launcher.launch(mimeTypes)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun AssignmentCard(
    assignmentScreenData: AssignmentScreenData,
    onDownloadClick: () -> Unit,
    onViewClick: () -> Unit,
    onSubmitClick: () -> Unit,
) {
    val statusColor =
        when {
            assignmentScreenData.isSubmitted -> Color(0xFF10B981)
            assignmentScreenData.isOverdue -> Color(0xFFEF4444)
            else -> Color(0xFFF59E0B)
        }

    val statusLabel =
        when {
            assignmentScreenData.isSubmitted -> "Selesai Dikirim"
            assignmentScreenData.isOverdue -> "Waktu Habis"
            else -> "Belum Dikirim"
        }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "card_scale")

    Card(
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            ),
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "%02d".format(assignmentScreenData.meetingNumber),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                Badge(
                    containerColor = statusColor.copy(alpha = 0.15f),
                    contentColor = statusColor,
                    modifier = Modifier.border(0.5.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tugas / Instruksi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            assignmentScreenData.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Batas Waktu: ${assignmentScreenData.deadline}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val dlInteraction = remember { MutableInteractionSource() }
                val isDlPressed by dlInteraction.collectIsPressedAsState()
                val dlScale by animateFloatAsState(if (isDlPressed) 0.94f else 1f, label = "dl_scale")

                val viewInteraction = remember { MutableInteractionSource() }
                val isViewPressed by viewInteraction.collectIsPressedAsState()
                val viewScale by animateFloatAsState(if (isViewPressed) 0.94f else 1f, label = "view_scale")

                assignmentScreenData.assignmentFileUrl?.let {
                    FilledTonalButton(
                        onClick = onDownloadClick,
                        modifier =
                            Modifier.weight(1f).graphicsLayer {
                                scaleX = dlScale
                                scaleY = dlScale
                            },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        interactionSource = dlInteraction,
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unduh Berkas", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                assignmentScreenData.submissionFileUrl?.let {
                    OutlinedButton(
                        onClick = onViewClick,
                        modifier =
                            Modifier.weight(1f).graphicsLayer {
                                scaleX = viewScale
                                scaleY = viewScale
                            },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        interactionSource = viewInteraction,
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Berkas Saya", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            if (!assignmentScreenData.isOverdue) {
                val submitInteraction = remember { MutableInteractionSource() }
                val isSubmitPressed by submitInteraction.collectIsPressedAsState()
                val submitScale by animateFloatAsState(if (isSubmitPressed) 0.95f else 1f, label = "submit_scale")

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSubmitClick,
                    modifier =
                        Modifier.fillMaxWidth().graphicsLayer {
                            scaleX = submitScale
                            scaleY = submitScale
                        },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = if (assignmentScreenData.isSubmitted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                        ),
                    interactionSource = submitInteraction,
                ) {
                    Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (assignmentScreenData.isSubmitted) "Ganti Jawaban Tugas" else "Kirim Jawaban Sekarang",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
