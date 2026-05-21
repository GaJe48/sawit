package com.gaje48.lms.di

import androidx.room.Room
import com.gaje48.lms.data.AuthRepository
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.data.LocalDataSource
import com.gaje48.lms.data.StorageDataSource
import com.gaje48.lms.data.db.LmsDatabase
import com.gaje48.lms.ui.MainViewModel
import com.gaje48.lms.ui.screens.assignment.AssignmentViewModel
import com.gaje48.lms.ui.screens.attendance.AttendanceViewModel
import com.gaje48.lms.ui.screens.content.ContentViewModel
import com.gaje48.lms.ui.screens.dashboard.DashboardViewModel
import com.gaje48.lms.ui.screens.login.LoginViewModel
import com.gaje48.lms.ui.screens.meeting.MeetingViewModel
import com.gaje48.lms.util.NotificationHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val module =
    module {
        single { NotificationHelper(androidContext()) }
        single { uniffi.lms_rust.InternetDataSource() }
        single { LocalDataSource(androidContext()) }
        single { StorageDataSource(androidContext()) }
        single {
            Room
                .databaseBuilder(
                    androidContext(),
                    LmsDatabase::class.java,
                    "lms.db",
                ).build()
        }
        single { get<LmsDatabase>().studentDao() }
        single { get<LmsDatabase>().courseDao() }
        single { get<LmsDatabase>().meetingDao() }
        single { get<LmsDatabase>().meetingContentDao() }
        single { get<LmsDatabase>().assignmentDao() }
        single { get<LmsDatabase>().attendanceDao() }
        singleOf(::AuthRepository)
        singleOf(::LmsRepository)
        viewModelOf(::MainViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::DashboardViewModel)
        viewModelOf(::MeetingViewModel)
        viewModelOf(::ContentViewModel)
        viewModelOf(::AssignmentViewModel)
        viewModelOf(::AttendanceViewModel)
    }
