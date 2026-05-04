package com.gaje48.lms.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.gaje48.lms.ui.screens.assignment.AssignmentScreen
import com.gaje48.lms.ui.screens.dashboard.DashboardScreen
import com.gaje48.lms.ui.screens.attendance.AttendanceScreen
import com.gaje48.lms.ui.screens.login.LoginScreen
import com.gaje48.lms.ui.screens.content.ContentScreen
import com.gaje48.lms.ui.screens.meeting.MeetingScreen
import com.gaje48.lms.ui.screens.dashboard.DashboardViewModel
import com.gaje48.lms.ui.screens.login.LoginViewModel
import com.gaje48.lms.ui.screens.content.ContentViewModel
import com.gaje48.lms.ui.screens.meeting.MeetingViewModel
import com.gaje48.lms.ui.screens.attendance.AttendanceViewModel
import com.gaje48.lms.ui.screens.assignment.AssignmentViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
object LoginNavKey : NavKey
@Serializable
object DashboardNavKey : NavKey
@Serializable
data class MeetingNavKey(val courseCode: String) : NavKey
@Serializable
data class ContentNavKey(val meetingUrl: String) : NavKey
@Serializable
data class AssignmentNavKey(val courseCode: String) : NavKey
@Serializable
data class AttendanceNavKey(val courseCode: String) : NavKey

@Composable
fun LmsApp(loginViewModel: LoginViewModel) {
    val backStack = rememberNavBackStack(LoginNavKey)
    val isLoggedIn by loginViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(null) {
        loginViewModel.checkLoginStatus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(isLoggedIn) {
        backStack.removeAt(0)
        backStack.add(0, if (isLoggedIn) DashboardNavKey else LoginNavKey)
    }

    val showSnackbar: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            onBack = { backStack.removeAt(backStack.lastIndex) },
            entryProvider = entryProvider {
                entry<LoginNavKey> {
                    LoginScreen(loginViewModel)
                }

                entry<DashboardNavKey> {
                    val viewModel = koinViewModel<DashboardViewModel>()
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(viewModel.snackbarEvent, lifecycleOwner) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.snackbarEvent.collect { showSnackbar(it) }
                        }
                    }
                    DashboardScreen(
                        viewModel = viewModel,
                        onCourseClick = { backStack.add(MeetingNavKey(it.courseCode)) },
                        onPresenceClick = { backStack.add(AttendanceNavKey(it.courseCode)) },
                        onTaskClick = { backStack.add(AssignmentNavKey(it.courseCode)) },
                    )
                }

                entry<MeetingNavKey> { destination ->
                    val viewModel = koinViewModel<MeetingViewModel> { parametersOf(destination.courseCode) }
                    MeetingScreen(
                        viewModel = viewModel,
                        onBackClick = { backStack.removeAt(backStack.lastIndex) },
                        onMeetingClick = { meetingUrl ->
                            backStack.add(ContentNavKey(meetingUrl))
                        }
                    )
                }

                entry<ContentNavKey> { destination ->
                    val viewModel = koinViewModel<ContentViewModel> { parametersOf(destination.meetingUrl) }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(viewModel.snackbarEvent, lifecycleOwner) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.snackbarEvent.collect { showSnackbar(it) }
                        }
                    }
                    ContentScreen(
                        viewModel = viewModel,
                        onBackClick = { backStack.removeAt(backStack.lastIndex) },
                    )
                }

                entry<AssignmentNavKey> { destination ->
                    val viewModel = koinViewModel<AssignmentViewModel> { parametersOf(destination.courseCode) }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(viewModel.snackbarEvent, lifecycleOwner) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.snackbarEvent.collect { showSnackbar(it) }
                        }
                    }
                    AssignmentScreen(
                        viewModel = viewModel,
                        onBackClick = { backStack.removeAt(backStack.lastIndex) }
                    )
                }

                entry<AttendanceNavKey> { destination ->
                    val viewModel = koinViewModel<AttendanceViewModel> { parametersOf(destination.courseCode) }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(viewModel.snackbarEvent, lifecycleOwner) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.snackbarEvent.collect { showSnackbar(it) }
                        }
                    }
                    AttendanceScreen(
                        viewModel = viewModel,
                        onBackClick = { backStack.removeAt(backStack.lastIndex) }
                    )
                }
            }
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}