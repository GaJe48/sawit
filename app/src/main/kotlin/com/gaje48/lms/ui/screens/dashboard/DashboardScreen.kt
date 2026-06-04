package com.gaje48.lms.ui.screens.dashboard

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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Student
import com.gaje48.lms.ui.components.EmptyGif
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

@Preview
@Composable
fun PreviewDashboardScreen() {
    val dummyProfile =
        Student(
            studentName = "Abi Musa Abdurrahman",
            npm = "202443500660",
            studyProgram = "Teknik Informatika S1",
            classCode = "R7A",
            studentProfilePictureUrl = "",
        )

    val dummyCourse =
        Course(
            courseCode = "TIF123",
            courseName = "Pemrograman Mobile",
            day = "Senin",
            clock = "08:00 - 10:00",
            room = "V.202",
            lecturerName = "Pak Dosen",
            lecturerPhoneNumber = "0812345",
            lecturerProfilePictureUrl = "",
        )

    val dummyList = listOf(dummyCourse, dummyCourse, dummyCourse, dummyCourse, dummyCourse)

    val dummyAttend =
        listOf(
            AttendancesByCourse(
                courseCode = "TIF123",
                attendances = listOf(true, true, false, true, true),
            ),
        )

    DashboardScreenStateless(
        student = dummyProfile,
        courses = dummyList,
        allAttendances = dummyAttend,
        onCourseClick = {},
        onAttendanceClick = {},
        onAssignmentClick = {},
        onLogout = {},
    )
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onCourseClick: (String) -> Unit,
    onAttendanceClick: (String) -> Unit,
    onAssignmentClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreenStateless(
        student = uiState.student ?: return,
        courses = uiState.courses,
        allAttendances = uiState.allPresences,
        onCourseClick = onCourseClick,
        onAttendanceClick = onAttendanceClick,
        onAssignmentClick = onAssignmentClick,
        onLogout = { viewModel.logout() },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeMaterialsApi::class,
)
@Composable
fun DashboardScreenStateless(
    student: Student,
    courses: List<Course>,
    allAttendances: List<AttendancesByCourse>,
    onCourseClick: (String) -> Unit,
    onAttendanceClick: (String) -> Unit,
    onAssignmentClick: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showLogoutDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "dash_blobs")
    val blobTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(25000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "blob_time",
    )

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    text = "Keluar Akun",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "Apakah kamu yakin ingin logout dari sistem? Kamu perlu masuk kembali menggunakan NIM dan password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLogout()
                        showLogoutDialog = false
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Logout", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        "Batal",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            modifier =
                Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp),
                    ),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(24.dp),
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier =
                Modifier
                    .size(450.dp)
                    .graphicsLayer {
                        translationX = (30f * kotlin.math.sin(blobTime)).dp.toPx() - 150.dp.toPx()
                        translationY = (30f * kotlin.math.cos(blobTime)).dp.toPx() - 100.dp.toPx()
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
                    .size(400.dp)
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        translationX = (40f * kotlin.math.cos(blobTime + 2f)).dp.toPx() + 100.dp.toPx()
                        translationY = (40f * kotlin.math.sin(blobTime + 2f)).dp.toPx() + 100.dp.toPx()
                    }.background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
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
                                text = "Halo, ${student.studentName.substringBefore(' ')}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                text = "Selamat datang kembali",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showLogoutDialog = true },
                            modifier =
                                Modifier
                                    .padding(end = 12.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        CircleShape,
                                    ).border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.error,
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
                item { StudentProfileCard(student = student) }

                if (courses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.6f),
                            contentAlignment = Alignment.Center,
                        ) { EmptyGif(label = "Belum ada jadwal mata kuliah") }
                    }

                    return@LazyColumn
                }

                item {
                    Text(
                        text = "Jadwal Kuliah Anda",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                items(
                    items = courses.zip(allAttendances),
                    key = { (course, _) -> course.courseCode },
                ) { (course, attendancesByCourse) ->
                    CourseCard(
                        course = course,
                        attendancesByCourse = attendancesByCourse,
                        onCourseClick = onCourseClick,
                        onAttendanceClick = onAttendanceClick,
                        onAssignmentClick = onAssignmentClick,
                    )
                }
            }
        }
    }
}

@Composable
fun StudentProfileCard(
    student: Student,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(80.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                ) {
                    if (!student.studentProfilePictureUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = student.studentProfilePictureUrl,
                            contentDescription = "Foto Profil",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.studentName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 26.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "NPM ${student.npm}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = student.studyProgram,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (!student.classCode.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kelas ${student.classCode}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CourseCard(
    course: Course,
    attendancesByCourse: AttendancesByCourse,
    onCourseClick: (String) -> Unit,
    onAttendanceClick: (String) -> Unit,
    onAssignmentClick: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "card_scale")

    Card(
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        onClick = { onCourseClick(course.courseCode) },
        shape = RoundedCornerShape(24.dp),
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
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.lecturerName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoChip(
                    icon = Icons.Default.CalendarMonth,
                    text = course.day,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                InfoChip(
                    icon = Icons.Default.MeetingRoom,
                    text = "Ruang " + course.room,
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AttendanceGraph(attendancesByCourse.attendances)

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = course.clock,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val abInteraction = remember { MutableInteractionSource() }
                    val isAbPressed by abInteraction.collectIsPressedAsState()
                    val abScale by animateFloatAsState(if (isAbPressed) 0.9f else 1f, label = "ab")

                    val tgInteraction = remember { MutableInteractionSource() }
                    val isTgPressed by tgInteraction.collectIsPressedAsState()
                    val tgScale by animateFloatAsState(if (isTgPressed) 0.9f else 1f, label = "tg")

                    Button(
                        onClick = { onAttendanceClick(course.courseCode) },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        interactionSource = abInteraction,
                        modifier =
                            Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp).graphicsLayer {
                                scaleX = abScale
                                scaleY = abScale
                            },
                    ) {
                        Text(
                            "Presensi",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    Button(
                        onClick = { onAssignmentClick(course.courseCode) },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.secondary,
                            ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        interactionSource = tgInteraction,
                        modifier =
                            Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp).graphicsLayer {
                                scaleX = tgScale
                                scaleY = tgScale
                            },
                    ) {
                        Text(
                            "Tugas",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(
    icon: ImageVector,
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = fontWeight,
            )
        }
    }
}

@Composable
fun AttendanceGraph(attendances: List<Boolean>) {
    val attendedCount = attendances.count { it }
    val percentage =
        if (attendances.isNotEmpty()) {
            ((attendedCount.toDouble() / attendances.size) * 100).roundToInt()
        } else {
            0
        }
    val upcomingCount = maxOf(0, 16 - attendances.size)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Track Kehadiran",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$percentage% ($attendedCount/${attendances.size})",
                style = MaterialTheme.typography.labelSmall,
                color = if (percentage >= 75) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            attendances.forEach { isPresent ->
                val boxColor = if (isPresent) Color(0xFF10B981) else Color(0xFFEF4444)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(boxColor),
                )
            }

            repeat(upcomingCount) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                )
            }
        }
    }
}
