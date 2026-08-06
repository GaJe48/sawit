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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.gaje48.lms.R
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Student
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
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

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
        unsubmittedCounts = emptyMap(),
        lastSyncText = "Terakhir sinkron: 15 Jun, 16:55",
        onCourseClick = { },
        onAttendanceClick = { },
        onAssignmentClick = { },
        onLogout = { },
    )
}
@Composable
fun DashboardScreen(component: DashboardComponent) {
    val uiState by component.uiState.subscribeAsState()

    DashboardScreenStateless(
        student = uiState.student ?: return,
        courses = uiState.courses,
        allAttendances = uiState.allPresences,
        unsubmittedCounts = uiState.unsubmittedCounts,
        lastSyncText = uiState.lastSyncText,
        onCourseClick = { component.onCourseClick(it) },
        onAttendanceClick = { component.onAttendanceClick(it) },
        onAssignmentClick = { component.onAssignmentClick(it) },
        onLogout = { component.logout() },
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
    unsubmittedCounts: Map<String, Int>,
    lastSyncText: String,
    onCourseClick: (String) -> Unit,
    onAttendanceClick: (String) -> Unit,
    onAssignmentClick: (String) -> Unit,
    onLogout: () -> Unit,
) {
    FloatingBlobsBackground {
        val scope = rememberCoroutineScope()

        val hazeState = rememberHazeState()

        var showLogoutDialog by remember { mutableStateOf(false) }

        if (showLogoutDialog) {
            AlertDialog(
                icon = {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null, Modifier.size(28.dp))
                },
                title = {
                    Text(
                        text = stringResource(R.string.dashboard_logout_title),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.dashboard_logout_desc),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    val confirmInteraction = remember { MutableInteractionSource() }
                    val isConfirmPressed by rememberPressedState(confirmInteraction)
                    val confirmScale by animateFloatAsState(
                        targetValue = if (isConfirmPressed) 0.95f else 1f,
                        label = "logout_confirm",
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                delay(150.milliseconds)
                                onLogout()
                                showLogoutDialog = false
                            }
                        },
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = confirmScale
                                scaleY = confirmScale
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        interactionSource = confirmInteraction,
                    ) { Text(text = stringResource(R.string.dashboard_logout_confirm), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    val dismissInteraction = remember { MutableInteractionSource() }
                    val isDismissPressed by rememberPressedState(dismissInteraction)
                    val dismissScale by animateFloatAsState(
                        targetValue = if (isDismissPressed) 0.95f else 1f,
                        label = "logout_dismiss",
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                delay(150.milliseconds)
                                showLogoutDialog = false
                            }
                        },
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = dismissScale
                                scaleY = dismissScale
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
                        interactionSource = dismissInteraction,
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_logout_dismiss),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                onDismissRequest = { showLogoutDialog = false },
                modifier =
                    Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                            shape = RoundedCornerShape(24.dp),
                        ),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface.copy(0.8f),
                iconContentColor = MaterialTheme.colorScheme.error,
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                    modifier = Modifier.hazeEffect(hazeState, HazeMaterials.ultraThin()),
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.dashboard_hello, student.studentName.substringBefore(' ')),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(6.dp)
                                            .background(
                                                color =
                                                    if (lastSyncText !=
                                                        stringResource(R.string.dashboard_not_synced_yet)
                                                    ) {
                                                        Color(0xFF10B981)
                                                    } else {
                                                        Color.Gray
                                                    },
                                                shape = CircleShape,
                                            ),
                                )
                                Text(
                                    text = lastSyncText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        val logoutBtnInteraction = remember { MutableInteractionSource() }
                        val isLogoutBtnPressed by rememberPressedState(logoutBtnInteraction)
                        val logoutBtnScale by animateFloatAsState(
                            targetValue = if (isLogoutBtnPressed) 0.95f else 1f,
                            label = "logout_btn_scale",
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    delay(150.milliseconds)
                                    showLogoutDialog = true
                                }
                            },
                            modifier =
                                Modifier.padding(end = 12.dp).size(50.dp).graphicsLayer {
                                    scaleX = logoutBtnScale
                                    scaleY = logoutBtnScale
                                },
                            shape = CircleShape,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
                            contentPadding = PaddingValues(),
                            interactionSource = logoutBtnInteraction,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = stringResource(R.string.dashboard_logout_confirm),
                                tint = MaterialTheme.colorScheme.error,
                            )
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
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer.copy(0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f)),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(80.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                ) {
                                    if (student.studentProfilePictureUrl != null) {
                                        AsyncImage(
                                            student.studentProfilePictureUrl,
                                            stringResource(R.string.dashboard_profile_picture_content_desc),
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

                                Spacer(Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = student.studentName,
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 26.sp,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 2,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.dashboard_npm, student.npm),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))

                            Spacer(Modifier.height(14.dp))

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
                                        text = stringResource(R.string.dashboard_class, student.classCode),
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }

                if (courses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.7f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyGif(label = stringResource(R.string.dashboard_no_schedule))
                        }
                    }

                    return@LazyColumn
                }

                item {
                    Text(
                        text = stringResource(R.string.dashboard_schedule_title),
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
                        unsubmittedCount = unsubmittedCounts[course.courseCode] ?: 0,
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
fun CourseCard(
    course: Course,
    attendancesByCourse: AttendancesByCourse,
    unsubmittedCount: Int,
    onCourseClick: (String) -> Unit,
    onAttendanceClick: (String) -> Unit,
    onAssignmentClick: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by rememberPressedState(interactionSource)
    val cardScale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "card_scale")

    Card(
        onClick = {
            scope.launch {
                delay(150.milliseconds)
                onCourseClick(course.courseCode)
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
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = course.lecturerName.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.labelSmall,
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = course.courseName,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 24.sp,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(0.2f),
                ) {
                    Icon(Icons.Default.School, null, Modifier.padding(10.dp), MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoBadge(
                    icon = Icons.Default.CalendarMonth,
                    text = course.day,
                    color = MaterialTheme.colorScheme.primary,
                )
                InfoBadge(
                    icon = Icons.Default.MeetingRoom,
                    text = stringResource(R.string.dashboard_room, course.room),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(Modifier.height(16.dp))

            AttendanceGraph(attendancesByCourse.attendances)

            Spacer(Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, Modifier.size(16.dp))

                Spacer(Modifier.width(6.dp))

                Text(
                    text = course.clock,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.weight(1f))

                val abInteraction = remember { MutableInteractionSource() }
                val isAbPressed by rememberPressedState(abInteraction)
                val abScale by animateFloatAsState(targetValue = if (isAbPressed) 0.95f else 1f, label = "ab")

                val tgInteraction = remember { MutableInteractionSource() }
                val isTgPressed by rememberPressedState(tgInteraction)
                val tgScale by animateFloatAsState(targetValue = if (isTgPressed) 0.95f else 1f, label = "tg")

                Button(
                    onClick = {
                        scope.launch {
                            delay(150.milliseconds)
                            onAttendanceClick(course.courseCode)
                        }
                    },
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = abScale
                            scaleY = abScale
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(0.2f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    interactionSource = abInteraction,
                ) {
                    Text(text = stringResource(R.string.dashboard_presence_button), fontSize = 12.sp, fontWeight = FontWeight.Black)
                }

                Spacer(Modifier.width(12.dp))

                BadgedBox(
                    badge = {
                        if (unsubmittedCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) {
                                Text(
                                    text = unsubmittedCount.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                delay(150.milliseconds)
                                onAssignmentClick(course.courseCode)
                            }
                        },
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = tgScale
                                scaleY = tgScale
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.secondary,
                            ),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        interactionSource = tgInteraction,
                    ) {
                        Text(stringResource(R.string.dashboard_assignment_button), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
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
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.dashboard_attendance_track),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.dashboard_attendance_percentage, percentage.toString(), attendedCount, attendances.size),
                style = MaterialTheme.typography.labelSmall,
                color = if (percentage >= 75) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(Modifier.height(8.dp))

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
