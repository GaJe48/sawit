package com.gaje48.lms.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushToFront
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.model.UpdateInfo
import com.gaje48.lms.ui.screens.assignment.AssignmentComponent
import com.gaje48.lms.ui.screens.assignment.DefaultAssignmentComponent
import com.gaje48.lms.ui.screens.attendance.AttendanceComponent
import com.gaje48.lms.ui.screens.attendance.DefaultAttendanceComponent
import com.gaje48.lms.ui.screens.dashboard.DashboardComponent
import com.gaje48.lms.ui.screens.dashboard.DefaultDashboardComponent
import com.gaje48.lms.ui.screens.login.DefaultLoginComponent
import com.gaje48.lms.ui.screens.login.LoginComponent
import com.gaje48.lms.ui.screens.meeting.DefaultMeetingComponent
import com.gaje48.lms.ui.screens.meeting.MeetingComponent
import com.github.michaelbull.result.onErr
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

data class AppUpdateState(
    val updateInfo: UpdateInfo? = null,
    val downloadProgress: Int? = null,
    val isDownloading: Boolean = false,
)

interface RootComponent : BackHandlerOwner {
    val childStack: Value<ChildStack<*, Child>>
    val snackbarEvent: Flow<String>
    val isRefreshing: Value<Boolean>
    val isSplashReady: Value<Boolean>
    val updateState: Value<AppUpdateState>
    val authState: Value<Boolean>

    fun checkLoginStatus()

    fun refresh()

    fun onNavigateToMeeting(courseCode: String)

    fun onNavigateToAssignment(courseCode: String)

    fun onNavigateToAttendance(courseCode: String)

    fun onBackClicked()
}

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent, ComponentContext by componentContext, KoinComponent {
    private val authRepository: AuthRepository = get()
    private val courseRepository: CourseRepository = get()

    private val navigation = StackNavigation<Config>()
    private val scope = coroutineScope()

    private val _snackbarEvent = Channel<String>(Channel.CONFLATED)
    override val snackbarEvent: Flow<String> = _snackbarEvent.receiveAsFlow()

    private val _isRefreshing = MutableValue(false)
    override val isRefreshing: Value<Boolean> = _isRefreshing

    private val _isSplashReady = MutableValue(false)
    override val isSplashReady: Value<Boolean> = _isSplashReady

    private val _updateState = MutableValue(AppUpdateState())
    override val updateState: Value<AppUpdateState> = _updateState

    private val _authState = MutableValue(false)
    override val authState: Value<Boolean> = _authState

    private fun createChild(config: Config, context: ComponentContext): Child = when (config) {
        is Config.Login -> Child.Login(DefaultLoginComponent(componentContext = context))

        is Config.Dashboard -> Child.Dashboard(
            DefaultDashboardComponent(
                componentContext = context,
                onNavigateToMeeting = { onNavigateToMeeting(it) },
                onNavigateToAttendance = { onNavigateToAttendance(it) },
                onNavigateToAssignment = { onNavigateToAssignment(it) },
            ),
        )

        is Config.Meeting -> Child.Meeting(
            DefaultMeetingComponent(
                componentContext = context,
                courseCode = config.courseCode,
                onBack = { onBackClicked() },
            ),
        )

        is Config.Assignment -> Child.Assignment(
            DefaultAssignmentComponent(
                componentContext = context,
                courseCode = config.courseCode,
                onBack = { onBackClicked() },
            ),
        )

        is Config.Attendance -> Child.Attendance(
            DefaultAttendanceComponent(
                componentContext = context,
                courseCode = config.courseCode,
                onBack = { onBackClicked() },
            ),
        )
    }

    override val childStack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Login,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    init {
        scope.launch {
            authRepository.isLoggedIn.collect { isLoggedIn ->
                _authState.value = isLoggedIn
                val targetConfig = if (isLoggedIn) Config.Dashboard else Config.Login
                val currentConfig = childStack.value.active.configuration

                if (isLoggedIn && currentConfig is Config.Login) {
                    navigation.replaceAll(targetConfig)
                } else if (!isLoggedIn && currentConfig !is Config.Login) {
                    navigation.replaceAll(targetConfig)
                }
            }
        }
    }

    override fun checkLoginStatus() {
        scope.launch {
            authRepository.isLoggedIn.first()
            _isSplashReady.value = true

            authRepository.savedCredential()?.let {
                authRepository.checkLoginStatus(it.first, it.second)
            }
        }
    }

    override fun refresh() {
        _isRefreshing.value = true

        scope.launch {
            courseRepository.syncAll().onErr {
                _snackbarEvent.send(it.message ?: "Failed to refresh data")
            }

            _isRefreshing.value = false
        }
    }

    override fun onNavigateToMeeting(courseCode: String) {
        navigation.pushToFront(Config.Meeting(courseCode))
    }

    override fun onNavigateToAssignment(courseCode: String) {
        navigation.pushToFront(Config.Assignment(courseCode))
    }

    override fun onNavigateToAttendance(courseCode: String) {
        navigation.pushToFront(Config.Attendance(courseCode))
    }

    override fun onBackClicked() {
        navigation.pop()
    }
}

interface Child {
    class Login(val component: LoginComponent) : Child

    class Dashboard(val component: DashboardComponent) : Child

    class Meeting(val component: MeetingComponent) : Child

    class Assignment(val component: AssignmentComponent) : Child

    class Attendance(val component: AttendanceComponent) : Child
}

@Serializable
private sealed interface Config {
    @Serializable
    object Login : Config

    @Serializable
    object Dashboard : Config

    @Serializable
    class Meeting(val courseCode: String) : Config

    @Serializable
    class Assignment(val courseCode: String) : Config

    @Serializable
    class Attendance(val courseCode: String) : Config
}
