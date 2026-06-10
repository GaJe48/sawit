package com.gaje48.lms.navigation

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.gaje48.lms.model.AssignmentNavKey
import com.gaje48.lms.model.AttendanceNavKey
import com.gaje48.lms.model.DashboardNavKey
import com.gaje48.lms.model.LoginNavKey
import com.gaje48.lms.model.MeetingNavKey
import com.gaje48.lms.ui.MainViewModel
import com.gaje48.lms.ui.components.SyncIndicator
import com.gaje48.lms.ui.screens.assignment.AssignmentScreen
import com.gaje48.lms.ui.screens.assignment.AssignmentViewModel
import com.gaje48.lms.ui.screens.attendance.AttendanceScreen
import com.gaje48.lms.ui.screens.attendance.AttendanceViewModel
import com.gaje48.lms.ui.screens.dashboard.DashboardScreen
import com.gaje48.lms.ui.screens.dashboard.DashboardViewModel
import com.gaje48.lms.ui.screens.login.LoginScreen
import com.gaje48.lms.ui.screens.login.LoginViewModel
import com.gaje48.lms.ui.screens.meeting.MeetingScreen
import com.gaje48.lms.ui.screens.meeting.MeetingViewModel
import com.gaje48.lms.ui.theme.LMSUnindraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LmsApp(mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotif by remember { mutableStateOf(true) }
    var hasAlarm by remember { mutableStateOf(true) }
    var canRequestNotif by remember { mutableStateOf(true) }
    var showBlocker by remember { mutableStateOf(false) }

    val checkPerms = {
        val notif = isNotifGranted(context)
        val alarm = isAlarmGranted(context)

        hasNotif = notif
        hasAlarm = alarm
        canRequestNotif = canRequestNotif(activity)
        showBlocker = !notif || !alarm
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            checkPerms()
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    checkPerms()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLoggedIn by mainViewModel.isLoggedIn.collectAsStateWithLifecycle()

    LMSUnindraTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LmsAppContent(
                isLoggedIn = isLoggedIn,
                mainViewModel = mainViewModel,
            )

            if (showBlocker) {
                BlockerDialog(
                    onNotif = {
                        if (canRequestNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val intent =
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }

                            context.startActivity(intent)
                        }
                    },
                    onAlarm = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }

                            context.startActivity(intent)
                        }
                    },
                    hasNotif = hasNotif,
                    hasAlarm = hasAlarm,
                    canRequestNotif = canRequestNotif,
                )
            }
        }
    }
}

private val LmsTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
    ).togetherWith(
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            targetOffset = { offsetForFullSlide -> offsetForFullSlide / 3 },
        ) +
            fadeOut(
                animationSpec = tween(durationMillis = 350, easing = LinearEasing),
                targetAlpha = 0.5f,
            ),
    )
}

private val LmsPopTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
    (
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            initialOffset = { offsetForFullSlide -> offsetForFullSlide / 3 },
        ) +
            fadeIn(
                animationSpec = tween(durationMillis = 350, easing = LinearEasing),
                initialAlpha = 0.5f,
            )
    ).togetherWith(
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        ),
    )
}

@Composable
private fun ObserveSnackbarEvents(
    flow: Flow<String>,
    showSnackbar: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { showSnackbar(it) }
        }
    }
}

@Composable
fun LmsAppContent(
    isLoggedIn: Boolean,
    mainViewModel: MainViewModel,
) {
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(if (isLoggedIn) DashboardNavKey else LoginNavKey)
    val pullToRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    val isRefreshing by mainViewModel.isRefreshing.collectAsStateWithLifecycle()

    val showSnackbar: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    ObserveSnackbarEvents(mainViewModel.snackbarEvent, showSnackbar)

    LaunchedEffect(isLoggedIn) {
        backStack.removeAt(0)
        backStack.add(0, if (isLoggedIn) DashboardNavKey else LoginNavKey)
    }

    val onBack: () -> Unit = { backStack.removeAt(backStack.lastIndex) }

    val content = @Composable {
        NavDisplay(
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            onBack = onBack,
            transitionSpec = LmsTransitionSpec,
            popTransitionSpec = LmsPopTransitionSpec,
            entryProvider =
                entryProvider {
                    entry<LoginNavKey> {
                        val loginViewModel = koinViewModel<LoginViewModel>()
                        LoginScreen(loginViewModel)
                    }

                    entry<DashboardNavKey> {
                        val viewModel = koinViewModel<DashboardViewModel>()
                        ObserveSnackbarEvents(viewModel.snackbarEvent, showSnackbar)

                        DashboardScreen(
                            viewModel = viewModel,
                            onCourseClick = { courseCode -> backStack.add(MeetingNavKey(courseCode)) },
                            onAttendanceClick = { courseCode -> backStack.add(AttendanceNavKey(courseCode)) },
                            onAssignmentClick = { courseCode -> backStack.add(AssignmentNavKey(courseCode)) },
                        )
                    }

                    entry<MeetingNavKey> { destination ->
                        val viewModel = koinViewModel<MeetingViewModel> { parametersOf(destination.courseCode) }
                        ObserveSnackbarEvents(viewModel.snackbarEvent, showSnackbar)

                        MeetingScreen(
                            viewModel = viewModel,
                            onBackClick = onBack,
                        )
                    }

                    entry<AssignmentNavKey> { destination ->
                        val viewModel = koinViewModel<AssignmentViewModel> { parametersOf(destination.courseCode) }
                        ObserveSnackbarEvents(viewModel.snackbarEvent, showSnackbar)

                        AssignmentScreen(
                            viewModel = viewModel,
                            onBackClick = onBack,
                        )
                    }

                    entry<AttendanceNavKey> { destination ->
                        val viewModel = koinViewModel<AttendanceViewModel> { parametersOf(destination.courseCode) }
                        ObserveSnackbarEvents(viewModel.snackbarEvent, showSnackbar)

                        AttendanceScreen(
                            viewModel = viewModel,
                            onBackClick = onBack,
                        )
                    }
                },
        )
    }

    if (!isLoggedIn) {
        content()
    } else {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            state = pullToRefreshState,
            onRefresh = { mainViewModel.refresh() },
            contentAlignment = Alignment.TopCenter,
            indicator = {
                SyncIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            content()

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun BlockerDialog(
    onNotif: () -> Unit,
    onAlarm: () -> Unit,
    hasNotif: Boolean,
    hasAlarm: Boolean,
    canRequestNotif: Boolean,
) {
    AlertDialog(
        onDismissRequest = { },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Peringatan",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = "Izin Diperlukan",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Aplikasi memerlukan izin berikut agar pemantauan tugas kuliah dapat berjalan secara andal di background. Aplikasi tidak dapat digunakan sampai izin diberikan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!hasNotif) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Izin Notifikasi",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Diperlukan untuk memberi tahu Anda secara instan jika ada tugas baru.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNotif,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = if (canRequestNotif) "Aktifkan Notifikasi" else "Buka Pengaturan Notifikasi",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                if (!hasAlarm) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Izin Alarm Presisi",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Diperlukan agar pengecekan tugas berjalan tepat waktu setiap 15 menit sekali.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onAlarm,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Buka Pengaturan Alarm", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { },
        modifier =
            Modifier
                .border(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                            ),
                        ),
                    shape = RoundedCornerShape(24.dp),
                ).clip(RoundedCornerShape(24.dp)),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    )
}

private fun isNotifGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun isAlarmGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

private fun canRequestNotif(activity: Activity?): Boolean {
    if (activity == null) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    val granted = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val rationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
    return granted || rationale
}
