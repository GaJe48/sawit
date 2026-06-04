package com.gaje48.lms.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
object LoginNavKey : NavKey

@Serializable
object DashboardNavKey : NavKey

@Serializable
data class MeetingNavKey(
    val courseCode: String,
) : NavKey

@Serializable
data class AssignmentNavKey(
    val courseCode: String,
) : NavKey

@Serializable
data class AttendanceNavKey(
    val courseCode: String,
) : NavKey

@Composable
fun LmsApp(mainViewModel: MainViewModel) {
    val isLoggedIn by mainViewModel.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val isRefreshing by mainViewModel.isRefreshing.collectAsStateWithLifecycle()

    val backStack = rememberNavBackStack(LoginNavKey)
    val pullToRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val showSnackbar: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    LaunchedEffect(isLoggedIn) {
        backStack.removeAt(0)
        backStack.add(0, if (isLoggedIn) DashboardNavKey else LoginNavKey)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(mainViewModel.snackbarEvent, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mainViewModel.snackbarEvent.collect { showSnackbar(it) }
        }
    }

    val content = @Composable {
        NavDisplay(
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            onBack = { backStack.removeAt(backStack.lastIndex) },
            transitionSpec = {
                (
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    ) + fadeIn(animationSpec = tween(durationMillis = 300))
                ).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    ) + fadeOut(animationSpec = tween(durationMillis = 300)),
                )
            },
            popTransitionSpec = {
                (
                    slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    ) + fadeIn(animationSpec = tween(durationMillis = 300))
                ).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    ) + fadeOut(animationSpec = tween(durationMillis = 300)),
                )
            },
            entryProvider =
                entryProvider {
                    entry<LoginNavKey> {
                        val loginViewModel = koinViewModel<LoginViewModel>()
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
                            onCourseClick = { courseCode -> backStack.add(MeetingNavKey(courseCode)) },
                            onAttendanceClick = { courseCode -> backStack.add(AttendanceNavKey(courseCode)) },
                            onAssignmentClick = { courseCode -> backStack.add(AssignmentNavKey(courseCode)) },
                        )
                    }

                    entry<MeetingNavKey> { destination ->
                        val viewModel = koinViewModel<MeetingViewModel> { parametersOf(destination.courseCode) }
                        val lifecycleOwner = LocalLifecycleOwner.current
                        LaunchedEffect(viewModel.snackbarEvent, lifecycleOwner) {
                            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.snackbarEvent.collect { showSnackbar(it) }
                            }
                        }

                        MeetingScreen(
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
                            onBackClick = { backStack.removeAt(backStack.lastIndex) },
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
                            onBackClick = { backStack.removeAt(backStack.lastIndex) },
                        )
                    }
                },
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!isLoggedIn) {
            content()

            return@Box
        }

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
        ) { content() }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
