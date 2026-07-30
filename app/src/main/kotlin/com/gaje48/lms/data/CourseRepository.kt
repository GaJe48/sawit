package com.gaje48.lms.data

import androidx.room.withTransaction
import com.gaje48.lms.data.db.AssignmentDao
import com.gaje48.lms.data.db.AttendanceDao
import com.gaje48.lms.data.db.ContentDao
import com.gaje48.lms.data.db.CourseDao
import com.gaje48.lms.data.db.LmsDatabase
import com.gaje48.lms.data.db.MeetingDao
import com.gaje48.lms.data.db.StudentDao
import com.gaje48.lms.data.db.toDomain
import com.gaje48.lms.data.db.toEntity
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import kotlinx.coroutines.flow.map

class CourseRepository(
    private val localDataSource: LocalDataSource,
    private val internetDataSource: uniffi.lms_rust.InternetDataSource,
    private val lmsDatabase: LmsDatabase,
    private val studentDao: StudentDao,
    private val courseDao: CourseDao,
    private val meetingDao: MeetingDao,
    private val attendanceDao: AttendanceDao,
    private val contentDao: ContentDao,
    private val assignmentDao: AssignmentDao,
) {
    val student = studentDao.observe().map { it?.toDomain() }

    val courses = courseDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    val lastSyncTime = localDataSource.lastSyncTime

    suspend fun syncAll(): Result<Unit, Throwable> = coroutineBinding {
        val lmsEntity = runSuspendCatching { internetDataSource.fetchAll() }.bind()

        lmsDatabase.withTransaction {
            studentDao.save(lmsEntity.student.toEntity())
            courseDao.save(lmsEntity.courses.map { it.toEntity() })
            meetingDao.save(lmsEntity.meetings.map { it.toEntity() })
            attendanceDao.save(lmsEntity.attendances.map { it.toEntity() })
            contentDao.save(lmsEntity.contents.map { it.toEntity() })
            assignmentDao.saveAll(lmsEntity.assignments.map { it.toEntity() })
        }

        localDataSource.saveLastSyncTime(System.currentTimeMillis())
    }
}
