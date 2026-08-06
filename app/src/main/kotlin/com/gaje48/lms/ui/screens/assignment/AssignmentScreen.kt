package com.gaje48.lms.ui.screens.assignment

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.gaje48.lms.R
import com.gaje48.lms.model.AssignmentScreenData
import com.gaje48.lms.ui.components.EmptyGif
import com.gaje48.lms.ui.components.FloatingBlobsBackground
import com.gaje48.lms.ui.components.InfoBadge
import com.gaje48.lms.ui.components.rememberPressedState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val mimeTypes =
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

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun AssignmentScreen(component: AssignmentComponent) {
    val uiState by component.uiState.subscribeAsState()
    val courseName = uiState.courseName ?: return

    AssignmentScreenStateless(
        courseName = courseName,
        assignmentScreenDatas = uiState.assignmentScreenDatas,
        onDownloadQuestion = { url, meetingNumber -> component.downloadQuestion(url, courseName, meetingNumber) },
        onUploadSubmission = { uri, assignmentUrl -> component.uploadSubmission(uri, assignmentUrl) },
        onBackClick = { component.onBackClick() },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun AssignmentScreenStateless(
    courseName: String,
    assignmentScreenDatas: List<AssignmentScreenData>,
    onDownloadQuestion: (String, Int) -> Unit,
    onUploadSubmission: (Uri, String) -> Unit,
    onBackClick: () -> Unit,
) {
    FloatingBlobsBackground {
        val hazeState = rememberHazeState()

        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                    modifier = Modifier.hazeEffect(hazeState, HazeMaterials.ultraThin()),
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.assignment_title),
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = courseName,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    navigationIcon = {
                        val scope = rememberCoroutineScope()

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
                            modifier =
                                Modifier.padding(start = 12.dp, end = 4.dp).size(50.dp).graphicsLayer {
                                    scaleX = backScale
                                    scaleY = backScale
                                },
                            shape = CircleShape,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f)),
                            contentPadding = PaddingValues(),
                            interactionSource = backInteraction,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.meeting_back_desc))
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) { paddingValues ->

            val uriHandler = LocalUriHandler.current

            var currentSubmitUrl by remember { mutableStateOf("") }
            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null && currentSubmitUrl.isNotEmpty()) {
                        onUploadSubmission(uri, currentSubmitUrl)
                    }
                }

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
                if (assignmentScreenDatas.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { EmptyGif(label = stringResource(R.string.assignment_no_assignments)) }
                    }

                    return@LazyColumn
                }

                items(items = assignmentScreenDatas, key = { it.assignmentUrl }) { assignmentScreenData ->
                    AssignmentCard(
                        assignmentScreenData = assignmentScreenData,
                        onDownloadClick = {
                            assignmentScreenData.assignmentFileUrl?.let { url ->
                                onDownloadQuestion(url, assignmentScreenData.meetingNumber)
                            }
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
    val scope = rememberCoroutineScope()

    val statusColor =
        when {
            assignmentScreenData.isSubmitted -> Color(0xFF10B981)
            assignmentScreenData.isOverdue -> Color(0xFFEF4444)
            else -> Color(0xFFF59E0B)
        }

    val statusLabel =
        when {
            assignmentScreenData.isSubmitted -> stringResource(R.string.assignment_submitted_status)
            assignmentScreenData.isOverdue -> stringResource(R.string.assignment_overdue_status)
            else -> stringResource(R.string.assignment_pending_status)
        }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface.copy(0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondary.copy(0.2f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(assignmentScreenData.meetingNumber),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                InfoBadge(
                    text = statusLabel,
                    color = statusColor,
                    modifier = Modifier.border(1.dp, statusColor.copy(0.2f), RoundedCornerShape(12.dp)),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.assignment_header),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (assignmentScreenData.description != null) {
                Text(
                    text = assignmentScreenData.description,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))

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
                    text = stringResource(R.string.assignment_deadline_formatted, assignmentScreenData.deadline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (assignmentScreenData.assignmentFileUrl != null) {
                    val dlInteraction = remember { MutableInteractionSource() }
                    val isDlPressed by rememberPressedState(dlInteraction)
                    val dlScale by animateFloatAsState(if (isDlPressed) 0.95f else 1f, label = "dl_scale")

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                delay(150)
                                onDownloadClick()
                            }
                        },
                        modifier =
                            Modifier.weight(1f).graphicsLayer {
                                scaleX = dlScale
                                scaleY = dlScale
                            },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        interactionSource = dlInteraction,
                    ) {
                        Icon(Icons.Default.Description, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.assignment_button_download), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (assignmentScreenData.submissionFileUrl != null) {
                    val viewInteraction = remember { MutableInteractionSource() }
                    val isViewPressed by rememberPressedState(viewInteraction)
                    val viewScale by animateFloatAsState(
                        targetValue = if (isViewPressed) 0.95f else 1f,
                        label = "view_scale",
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                delay(150)
                                onViewClick()
                            }
                        },
                        modifier =
                            Modifier.weight(1f).graphicsLayer {
                                scaleX = viewScale
                                scaleY = viewScale
                            },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        interactionSource = viewInteraction,
                    ) {
                        Icon(Icons.Default.Description, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.assignment_button_my_file), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!assignmentScreenData.isOverdue) {
                val submitInteraction = remember { MutableInteractionSource() }
                val isSubmitPressed by rememberPressedState(submitInteraction)
                val submitScale by animateFloatAsState(
                    targetValue = if (isSubmitPressed) 0.95f else 1f,
                    label = "submit_scale",
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            delay(150)
                            onSubmitClick()
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth().graphicsLayer {
                            scaleX = submitScale
                            scaleY = submitScale
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                if (assignmentScreenData.isSubmitted) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            contentColor = Color.White,
                        ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    interactionSource = submitInteraction,
                ) {
                    Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text =
                            if (assignmentScreenData.isSubmitted) {
                                stringResource(
                                    R.string.assignment_button_change,
                                )
                            } else {
                                stringResource(R.string.assignment_button_submit)
                            },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewAssignmentScreen() {
    AssignmentScreenStateless(
        courseName = "Pemrograman Visual Lanjut",
        assignmentScreenDatas =
            listOf(
                AssignmentScreenData(
                    assignmentUrl = "assignment-1",
                    meetingUrl = "meeting-1",
                    meetingNumber = 1,
                    description = "Silakan kerjakan tugas membuat antarmuka login menggunakan Compose.",
                    assignmentFileUrl = "file-1",
                    deadline = "2026-06-15 23:59:59",
                    submissionFileUrl = null,
                    isSubmitted = false,
                    isOverdue = false,
                ),
                AssignmentScreenData(
                    assignmentUrl = "assignment-2",
                    meetingUrl = "meeting-2",
                    meetingNumber = 2,
                    description = "Kirimkan laporan analisis kebutuhan sistem.",
                    assignmentFileUrl = null,
                    deadline = "2026-06-01 23:59:59",
                    submissionFileUrl = "submission-2",
                    isSubmitted = true,
                    isOverdue = false,
                ),
            ),
        onDownloadQuestion = { _, _ -> },
        onUploadSubmission = { _, _ -> },
        onBackClick = { },
    )
}
