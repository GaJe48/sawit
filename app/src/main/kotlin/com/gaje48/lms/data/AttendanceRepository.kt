package com.gaje48.lms.data

import com.gaje48.lms.data.db.AttendanceDao
import com.gaje48.lms.data.db.ContentDao
import com.gaje48.lms.data.db.toEntity
import com.gaje48.lms.model.AttendancesByCourse
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AttendanceRepository(
    private val internetDataSource: uniffi.lms_rust.InternetDataSource,
    private val attendanceDao: AttendanceDao,
    private val contentDao: ContentDao,
) {
    val allAttendances = attendanceDao.observeAll().map { entities ->
        entities.groupBy { it.courseCode }.map { (courseCode, attendances) ->
            AttendancesByCourse(courseCode, attendances.map { it.isAttended })
        }
    }

    fun observeAttendances(courseCode: String) =
        attendanceDao.observeByCourse(courseCode).map { entities ->
            entities.map { it.isAttended }
        }

    fun observeAttendanceVmDatas(courseCode: String) = contentDao.observeAttendanceVmDatas(courseCode)

    suspend fun isAttendanceAttended(courseCode: String, meetingNumber: Int): Boolean =
        withContext(Dispatchers.IO) {
            attendanceDao.isAttended(courseCode, meetingNumber)
        }

    suspend fun syncAttendancesByCourse(courseCode: String): Result<Unit, Throwable> = coroutineBinding {
        withContext(Dispatchers.IO) {
            val attendances = runSuspendCatching {
                internetDataSource.fetchAttendancesByCourse(courseCode)
            }.bind()

            attendanceDao.save(attendances.map { it.toEntity() })
        }
    }

    suspend fun executeAttendances(urls: List<String>): Result<Unit, Throwable> = runSuspendCatching {
        internetDataSource.executeAttendances(urls)
    }
}
