package com.gaje48.lms.ui.screens.dashboard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.gaje48.lms.ui.components.InfoBadge
import com.gaje48.lms.ui.components.rememberPressedState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            studentProfilePictureUrl = null,
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
            lecturerProfilePictureUrl = null,
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
        onCourseClick = { },
        onAttendanceClick = { },
        onAssignmentClick = { },
        onLogout = { },
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
    val scope = rememberCoroutineScope()

    val hazeState = rememberHazeState()

    val confirmInteraction = remember { MutableInteractionSource() }
    val isConfirmPressed by rememberPressedState(confirmInteraction)
    val confirmScale by animateFloatAsState(if (isConfirmPressed) 0.95f else 1f, label = "logout_confirm")

    val dismissInteraction = remember { MutableInteractionSource() }
    val isDismissPressed by rememberPressedState(dismissInteraction)
    val dismissScale by animateFloatAsState(if (isDismissPressed) 0.95f else 1f, label = "logout_dismiss")

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    text = "Keluar Akun",
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "Apakah kamu yakin ingin logout dari sistem? Kamu perlu masuk kembali menggunakan NIM dan password.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            delay(150)
                            onLogout()
                        }
                    },
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = confirmScale
                            scaleY = confirmScale
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    interactionSource = confirmInteraction,
                ) { Text(text = "Logout", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Button(
                    onClick = {
                        scope.launch {
                            delay(150)
                            showLogoutDialog = false
                        }
                    },
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = dismissScale
                            scaleY = dismissScale
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                    interactionSource = dismissInteraction,
                ) {
                    Text(
                        text = "Batal",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            onDismissRequest = { showLogoutDialog = false },
            modifier =
                Modifier
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        RoundedCornerShape(24.dp),
                    ),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            iconContentColor = MaterialTheme.colorScheme.error,
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.hazeEffect(hazeState, HazeMaterials.ultraThin()),
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
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                actions = {
                    val logoutBtnInteraction = remember { MutableInteractionSource() }
                    val isLogoutBtnPressed by rememberPressedState(logoutBtnInteraction)
                    val logoutBtnScale by animateFloatAsState(if (isLogoutBtnPressed) 0.95f else 1f, label = "logout_btn_scale")

                    Button(
                        onClick = {
                            scope.launch {
                                delay(150)
                                showLogoutDialog = true
                            }
                        },
                        interactionSource = logoutBtnInteraction,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                        shape = CircleShape,
                        contentPadding = PaddingValues(),
                        modifier =
                            Modifier.padding(end = 12.dp).size(50.dp).graphicsLayer {
                                scaleX = logoutBtnScale
                                scaleY = logoutBtnScale
                            },
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
            modifier = Modifier.hazeSource(hazeState),
            contentPadding = PaddingValues(20.dp, paddingValues.calculateTopPadding() + 16.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { StudentProfileCard(student = student) }

            if (courses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxHeight(0.7f),
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

@Composable
fun StudentProfileCard(student: Student) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            ),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(80.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                ) {
                    if (student.studentProfilePictureUrl != null) {
                        AsyncImage(
                            model = student.studentProfilePictureUrl,
                            contentDescription = "Foto Profil",
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

                Column {
                    Text(
                        text = student.studentName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoBadge(
                    icon = Icons.Default.School,
                    text = student.studyProgram,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (student.classCode != null) {
                    InfoBadge(
                        icon = Icons.Default.MeetingRoom,
                        text = "Kelas ${student.classCode}",
                        color = MaterialTheme.colorScheme.secondary,
                    )
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
        onClick = {
            scope.launch {
                delay(150)
                onCourseClick(course.courseCode)
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
        interactionSource = interactionSource,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
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
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoBadge(
                    icon = Icons.Default.CalendarMonth,
                    text = course.day,
                    color = MaterialTheme.colorScheme.primary,
                )
                InfoBadge(
                    icon = Icons.Default.MeetingRoom,
                    text = "Ruang " + course.room,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AttendanceGraph(attendancesByCourse.attendances)

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = course.clock,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.weight(1f))

                val abInteraction = remember { MutableInteractionSource() }
                val isAbPressed by rememberPressedState(abInteraction)
                val abScale by animateFloatAsState(if (isAbPressed) 0.95f else 1f, label = "ab")

                val tgInteraction = remember { MutableInteractionSource() }
                val isTgPressed by rememberPressedState(tgInteraction)
                val tgScale by animateFloatAsState(if (isTgPressed) 0.95f else 1f, label = "tg")

                Button(
                    onClick = {
                        scope.launch {
                            delay(150)
                            onAttendanceClick(course.courseCode)
                        }
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    interactionSource = abInteraction,
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = abScale
                            scaleY = abScale
                        },
                ) {
                    Text(
                        "Presensi",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            delay(150)
                            onAssignmentClick(course.courseCode)
                        }
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.secondary,
                        ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    interactionSource = tgInteraction,
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = tgScale
                            scaleY = tgScale
                        },
                ) {
                    Text(
                        "Tugas",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
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
    val upcomingCount = 16 - attendances.size

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Track Kehadiran",
                style = MaterialTheme.typography.labelMedium,
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

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}
