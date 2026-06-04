package com.gaje48.lms.ui.screens.meeting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gaje48.lms.R
import com.gaje48.lms.ui.components.EmptyGif
import com.gaje48.lms.ui.screens.dashboard.InfoChip
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
fun MeetingScreen(
    viewModel: MeetingViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedMeetingUrl by remember { mutableStateOf<String?>(null) }

    val course = uiState.course ?: return
    val meetings = uiState.meetings

    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val infiniteTransition = rememberInfiniteTransition(label = "meeting_blobs")
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                                text = course.courseName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = course.courseCode,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                        modifier = Modifier.size(52.dp),
                                    ) {
                                        if (!course.lecturerProfilePictureUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = course.lecturerProfilePictureUrl,
                                                contentDescription = "Foto Profil Dosen",
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.padding(12.dp),
                                                tint = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = course.lecturerName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = course.lecturerPhoneNumber ?: "Nomor HP tidak tersedia",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }

                                if (!course.lecturerPhoneNumber.isNullOrEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = { },
                                            modifier =
                                                Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                        CircleShape,
                                                    ).size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Salin Nomor HP",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val chipBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                                val chipColor = MaterialTheme.colorScheme.secondary
                                InfoChip(
                                    icon = Icons.Default.CalendarMonth,
                                    text = course.day,
                                    containerColor = chipBg,
                                    contentColor = chipColor,
                                    fontWeight = FontWeight.Bold,
                                )
                                InfoChip(
                                    icon = Icons.Default.Timer,
                                    text = course.clock,
                                    containerColor = chipBg,
                                    contentColor = chipColor,
                                    fontWeight = FontWeight.Bold,
                                )
                                InfoChip(
                                    icon = Icons.Default.MeetingRoom,
                                    text = "Ruang " + course.room,
                                    containerColor = chipBg,
                                    contentColor = chipColor,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                if (meetings.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.7f),
                            contentAlignment = Alignment.Center,
                        ) { EmptyGif(label = "Belum ada daftar pertemuan") }
                    }
                    return@LazyColumn
                }

                item {
                    Text(
                        text = "Daftar Pertemuan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                items(
                    items = meetings,
                    key = { meeting -> meeting.meetingUrl },
                ) { meeting ->
                    val isExpanded = expandedMeetingUrl == meeting.meetingUrl
                    MeetingCard(
                        index = meeting.meetingNumber,
                        meetingUrl = meeting.meetingUrl,
                        isExpanded = isExpanded,
                        viewModel = viewModel,
                        onDownloadFile = { fileUrl ->
                            viewModel.downloadFile(fileUrl, meeting.meetingUrl)
                        },
                        onMeetingClick = {
                            expandedMeetingUrl = if (isExpanded) null else meeting.meetingUrl
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingCard(
    index: Byte,
    meetingUrl: String,
    isExpanded: Boolean,
    viewModel: MeetingViewModel,
    onDownloadFile: (String) -> Unit,
    onMeetingClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "card_scale")
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "rotation_angle",
    )

    val contents by viewModel
        .observeContentVmDatas(meetingUrl)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val (files, links) =
        remember(contents) {
            val fileKeywords = listOf("pdf", "word", "powerpoint", "excel", "archive")
            contents.partition { item ->
                fileKeywords.any { keyword ->
                    item.type.contains(keyword, ignoreCase = true)
                }
            }
        }

    val uriHandler = LocalUriHandler.current

    Card(
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        shape = RoundedCornerShape(24.dp),
        onClick = onMeetingClick,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            ),
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            ),
        interactionSource = interactionSource,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(18.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "%02d".format(index),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Pertemuan Ke-$index",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Ketuk untuk melihat file materi & sesi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = rotationAngle }.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (contents.isEmpty()) {
                        Text(
                            text = "Tidak ada file materi yang tersedia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                        )
                    }

                    if (files.isNotEmpty()) {
                        SectionHeader(
                            icon = Icons.Default.Description,
                            label = "File Pembelajaran",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        files.forEach { item ->
                            key(item.contentUrl) {
                                ContentCard(
                                    title = item.title,
                                    description = "Ketuk untuk mengunduh berkas",
                                    icon = iconPainter(item.type),
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    onClick = { onDownloadFile(item.contentUrl) },
                                )
                            }
                        }
                    }

                    if (links.isNotEmpty()) {
                        SectionHeader(
                            icon = Icons.Default.Link,
                            label = "Tautan Pendukung",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        links.forEach { item ->
                            key(item.contentUrl) {
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
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            ),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(52.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
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
