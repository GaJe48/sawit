package com.gaje48.lms.ui.screens.assignment

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaje48.lms.model.AssignmentScreenData
import com.gaje48.lms.model.UpdateAction
import com.gaje48.lms.ui.components.EmptyGif
import com.gaje48.lms.ui.components.ErrorGif
import com.gaje48.lms.ui.components.LoadingGif
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
    val isLoading = uiState.isLoading
    val isRefreshing = uiState.isRefreshing
    val errorMessage = uiState.errorMessage

    val uriHandler = LocalUriHandler.current
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state = rememberPullToRefreshState()

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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.surface,
                                ),
                        ),
                    ),
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            state = state,
            onRefresh = { viewModel.fetchAssignments(UpdateAction.REFRESH) },
            contentAlignment = Alignment.TopCenter,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.padding(top = 16.dp),
                )
            },
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                topBar = {
                    LargeTopAppBar(
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
                                    "Tugas Kuliah",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    courseName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBackClick,
                                modifier =
                                    Modifier
                                        .padding(start = 8.dp, end = 8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            CircleShape,
                                        ),
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
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (assignmentScreenDatas.isEmpty()) {
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
                                            onRetry = { viewModel.fetchAssignments() },
                                        )

                                    else -> EmptyGif(label = "Belum ada tugas")
                                }
                            }
                        }
                    } else {
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
                                    assignmentScreenData.assignmentFileUrl?.let(viewModel::downloadAssignmentFile)
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
            assignmentScreenData.isSubmitted -> MaterialTheme.colorScheme.primary
            assignmentScreenData.isOverdue -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.secondary
        }

    val statusLabel =
        when {
            assignmentScreenData.isSubmitted -> "Sudah Dikumpulkan"
            assignmentScreenData.isOverdue -> "Waktu Berakhir"
            else -> "Belum Dikumpulkan"
        }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (assignmentScreenData.meetingNumber).toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                Badge(containerColor = statusColor.copy(alpha = 0.1f), contentColor = statusColor) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Deskripsi Tugas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            assignmentScreenData.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Batas: ${assignmentScreenData.deadline}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                assignmentScreenData.assignmentFileUrl?.let {
                    FilledTonalButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unduh Lampiran", fontWeight = FontWeight.Bold)
                    }
                }

                assignmentScreenData.submissionFileUrl?.let {
                    OutlinedButton(
                        onClick = onViewClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Jawaban Saya", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!assignmentScreenData.isOverdue) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSubmitClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    Icon(Icons.Default.FileUpload, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (assignmentScreenData.isSubmitted) {
                            "Ganti Jawaban"
                        } else {
                            "Kumpulkan Sekarang"
                        },
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}
