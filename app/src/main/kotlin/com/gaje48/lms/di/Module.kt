package com.gaje48.lms.di

import androidx.room.Room
import com.gaje48.lms.data.AssignmentRepository
import com.gaje48.lms.data.AttendanceRepository
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.CourseRepository
import com.gaje48.lms.data.LocalDataSource
import com.gaje48.lms.data.MeetingRepository
import com.gaje48.lms.data.StorageDataSource
import com.gaje48.lms.data.TransferRepository
import com.gaje48.lms.data.db.LmsDatabase
import com.gaje48.lms.services.LmsSyncScheduler
import com.gaje48.lms.ui.MainViewModel
import com.gaje48.lms.ui.screens.assignment.AssignmentViewModel
import com.gaje48.lms.ui.screens.attendance.AttendanceViewModel
import com.gaje48.lms.ui.screens.dashboard.DashboardViewModel
import com.gaje48.lms.ui.screens.login.LoginViewModel
import com.gaje48.lms.ui.screens.meeting.MeetingViewModel
import com.gaje48.lms.util.CredentialProviderImpl
import com.gaje48.lms.util.NotificationHelper
import com.gaje48.lms.util.TransferHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uniffi.lms_rust.CredentialProvider
import uniffi.lms_rust.InternetDataSource

val module =
    module {
        single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single { NotificationHelper(androidContext()) }
        single { TransferHelper(androidContext()) }
        single<CredentialProvider> { CredentialProviderImpl(get()) }
        single { LmsSyncScheduler(androidContext()) }
        single { InternetDataSource(get<CredentialProvider>()) }
        single { LocalDataSource(androidContext()) }
        single { StorageDataSource(androidContext()) }
        single { Room.databaseBuilder(androidContext(), LmsDatabase::class.java, "lms.db").build() }
        single { get<LmsDatabase>().studentDao() }
        single { get<LmsDatabase>().courseDao() }
        single { get<LmsDatabase>().meetingDao() }
        single { get<LmsDatabase>().meetingContentDao() }
        single { get<LmsDatabase>().assignmentDao() }
        single { get<LmsDatabase>().attendanceDao() }
        singleOf(::AuthRepository)
        singleOf(::CourseRepository)
        singleOf(::MeetingRepository)
        singleOf(::AttendanceRepository)
        singleOf(::AssignmentRepository)
        singleOf(::TransferRepository)
        viewModelOf(::MainViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::DashboardViewModel)
        viewModelOf(::MeetingViewModel)
        viewModelOf(::AssignmentViewModel)
        viewModelOf(::AttendanceViewModel)
    }
