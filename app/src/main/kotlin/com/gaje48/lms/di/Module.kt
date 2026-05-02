package com.gaje48.lms.di

import androidx.room.Room
import com.gaje48.lms.data.InternetDataSource
import com.gaje48.lms.data.LocalDataSource
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.data.StorageDataSource
import com.gaje48.lms.data.db.LmsDatabase
import com.gaje48.lms.ui.state.LmsViewModel
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val module = module {
    single { Dispatchers.IO }
    single { NotificationHelper(androidContext()) }
    single { InternetDataSource(get()) }
    single { LocalDataSource(androidContext()) }
    single { StorageDataSource(androidContext()) }
    single {
        Room.databaseBuilder(
            androidContext(),
            LmsDatabase::class.java,
            "lms.db"
        ).build()
    }
    single { get<LmsDatabase>().studentDao() }
    single { get<LmsDatabase>().courseDao() }
    single { get<LmsDatabase>().meetingDao() }
    single { get<LmsDatabase>().meetingContentDao() }
    single { get<LmsDatabase>().assignmentDao() }
    single { get<LmsDatabase>().attendanceDao() }
    single {
        LmsRepository(
            internetDataSource = get(),
            storageDataSource = get(),
            localDataSource = get(),
//            studentDao = get(),
//            courseDao = get(),
//            meetingDao = get(),
//            meetingContentDao = get(),
//            assignmentDao = get(),
//            attendanceDao = get()
        )
    }
    viewModel { LmsViewModel(get(), get()) }
}
