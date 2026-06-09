package com.gaje48.lms.ui.screens.meeting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gaje48.lms.R
import com.gaje48.lms.model.ContentVmData
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Meeting
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun MeetingScreen(
    viewModel: MeetingViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val course = uiState.course ?: return

    MeetingScreenStateless(
        course = course,
        meetings = uiState.meetings,
        observeContent = { viewModel.observeContent(it) },
        onDownloadFile = { fileUrl, meetingUrl -> viewModel.downloadFile(fileUrl, meetingUrl) },
        onBackClick = onBackClick,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun MeetingScreenStateless(
    course: Course,
    meetings: List<Meeting>,
    observeContent: (String) -> Flow<Pair<List<ContentVmData>, List<ContentVmData>>>,
    onDownloadFile: (String, String) -> Unit,
    onBackClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val hazeState = rememberHazeState()

    var expandedMeetingUrl by remember { mutableStateOf<String?>(null) }

    FloatingBlobsBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                    modifier = Modifier.hazeEffect(hazeState, HazeMaterials.ultraThin()),
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
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
                            contentPadding = PaddingValues(),
                            interactionSource = backInteraction,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) { paddingValues ->
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
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors =
                            CardDefaults.cardColors(
                                MaterialTheme.colorScheme.surface.copy(0.4f),
                            ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary.copy(0.2f),
                                ) {
                                    if (course.lecturerProfilePictureUrl != null) {
                                        AsyncImage(course.lecturerProfilePictureUrl, "Foto Profil Dosen")
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.padding(12.dp),
                                            tint = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = course.lecturerName,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = course.lecturerPhoneNumber ?: "Nomor HP tidak tersedia",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }

                                if (course.lecturerPhoneNumber != null) {
                                    val launchWaInteraction = remember { MutableInteractionSource() }
                                    val isLaunchWaPressed by rememberPressedState(launchWaInteraction)
                                    val launchWaScale by animateFloatAsState(
                                        targetValue = if (isLaunchWaPressed) 0.95f else 1f,
                                        label = "wa_scale",
                                    )

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                delay(150)
                                                uriHandler.openUri("https://wa.me/62" + course.lecturerPhoneNumber)
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .padding(end = 4.dp)
                                                .graphicsLayer {
                                                    scaleX = launchWaScale
                                                    scaleY = launchWaScale
                                                }.border(
                                                    1.dp,
                                                    Color(0xFF25D366).copy(alpha = 0.2f),
                                                    CircleShape,
                                                ).size(36.dp),
                                        colors =
                                            IconButtonDefaults.iconButtonColors(
                                                containerColor = Color(0xFF25D366).copy(0.2f),
                                                contentColor = Color(0xFF25D366),
                                            ),
                                        interactionSource = launchWaInteraction,
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.wa),
                                            contentDescription = "Hubungi WhatsApp",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val chipColor = MaterialTheme.colorScheme.secondary
                                InfoBadge(
                                    icon = Icons.Default.CalendarMonth,
                                    text = course.day,
                                    color = chipColor,
                                )
                                InfoBadge(
                                    icon = Icons.Default.Timer,
                                    text = course.clock,
                                    color = chipColor,
                                )
                                InfoBadge(
                                    icon = Icons.Default.MeetingRoom,
                                    text = "Ruang " + course.room,
                                    color = chipColor,
                                )
                            }
                        }
                    }
                }

                if (meetings.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.7f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyGif(label = "Belum ada daftar pertemuan")
                        }
                    }
                    return@LazyColumn
                }

                item {
                    Text(
                        text = "Daftar Pertemuan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }

                items(items = meetings, key = { meeting -> meeting.meetingUrl }) { meeting ->
                    val isExpanded = expandedMeetingUrl == meeting.meetingUrl
                    MeetingCard(
                        index = meeting.meetingNumber,
                        meetingUrl = meeting.meetingUrl,
                        isExpanded = isExpanded,
                        observeContent = observeContent,
                        onDownloadFile = { onDownloadFile(it, meeting.meetingUrl) },
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
    observeContent: (String) -> Flow<Pair<List<ContentVmData>, List<ContentVmData>>>,
    onDownloadFile: (String) -> Unit,
    onMeetingClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val (files, links) =
        observeContent(meetingUrl)
            .collectAsStateWithLifecycle(
                initialValue = Pair(emptyList(), emptyList()),
            ).value

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberPressedState(interactionSource)
    val cardScale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "card_scale")

    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotation_angle")

    Card(
        onClick = {
            scope.launch {
                delay(150)
                onMeetingClick()
            }
        },
        modifier =
            Modifier.graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface.copy(0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
        interactionSource = interactionSource,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(index),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Pertemuan Ke-$index",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Ketuk untuk melihat file materi & sesi",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = rotationAngle }.size(24.dp),
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (files.isEmpty() && links.isEmpty()) {
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
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberPressedState(interactionSource)
    val cardScale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "card_scale")

    Card(
        modifier =
            Modifier.graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        shape = RoundedCornerShape(24.dp),
        onClick = {
            scope.launch {
                delay(150)
                onClick()
            }
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.2f),
                contentColor = accentColor,
                modifier = Modifier.size(44.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
            ) {
                Icon(icon, null, Modifier.padding(10.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
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

@Preview
@Composable
fun PreviewMeetingScreen() {
    MeetingScreenStateless(
        course =
            Course(
                courseCode = "INF401",
                courseName = "Pemrograman Visual Lanjut",
                lecturerName = "Dr. Budi Santoso",
                lecturerPhoneNumber = "08123456789",
                lecturerProfilePictureUrl = null,
                day = "Senin",
                clock = "08:00 - 10:30",
                room = "Lab A",
            ),
        meetings =
            listOf(
                Meeting(
                    meetingNumber = 1,
                    meetingUrl = "url-1",
                ),
                Meeting(
                    meetingNumber = 2,
                    meetingUrl = "url-2",
                ),
            ),
        observeContent = { flowOf(Pair(emptyList(), emptyList())) },
        onDownloadFile = { _, _ -> },
        onBackClick = { },
    )
}
