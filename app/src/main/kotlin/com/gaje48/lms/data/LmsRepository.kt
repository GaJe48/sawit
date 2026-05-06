package com.gaje48.lms.data

import android.net.Uri
import androidx.room.withTransaction
import com.gaje48.lms.data.db.AssignmentDao
import com.gaje48.lms.data.db.AttendanceDao
import com.gaje48.lms.data.db.ContentDao
import com.gaje48.lms.data.db.CourseDao
import com.gaje48.lms.data.db.LmsDatabase
import com.gaje48.lms.data.db.MeetingDao
import com.gaje48.lms.data.db.StudentDao
import com.gaje48.lms.data.db.toAttendanceEntities
import com.gaje48.lms.data.db.toDomain
import com.gaje48.lms.data.db.toEntity
import com.gaje48.lms.data.db.toMeetingEntities
import com.gaje48.lms.model.AccountProblemException
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.SessionExpiredException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class LmsRepository(
    private val internetDataSource: InternetDataSource,
    private val storageDataSource: StorageDataSource,
    private val localDataSource: LocalDataSource,
    private val lmsDatabase: LmsDatabase,
    private val studentDao: StudentDao,
    private val courseDao: CourseDao,
    private val meetingDao: MeetingDao,
    private val contentDao: ContentDao,
    private val assignmentDao: AssignmentDao,
    private val attendanceDao: AttendanceDao
) {

    val student = studentDao.observe().map { it.toDomain() }

    val courses = courseDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    val allAttendances = attendanceDao.observeAll().map { entities ->
        entities.groupBy { it.courseCode }.map { (courseCode, attendances) ->
            AttendancesByCourse(courseCode, attendances.map { it.isAttended })
        }
    }

    fun observeMeetings(courseCode: String) = meetingDao.observeByCourse(courseCode).map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeContents(meetingUrl: String) = contentDao.observeByMeeting(meetingUrl).map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeAttendances(courseCode: String) = attendanceDao.observeByCourse(courseCode).map { entities ->
        entities.map { it.isAttended }
    }

    fun observeAttendanceVmDatas(courseCode: String) = contentDao.observeAttendanceVmDatas(courseCode)

    fun observeAssignmentScreenDatas(courseCode: String) = assignmentDao.observeAssignmentScreenDatas(courseCode)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    suspend fun savedCredential() = localDataSource.getCredentials()

    suspend fun clearCredential() {
        localDataSource.clearCredentials()
        _isLoggedIn.value = false
        studentDao.clear()
        courseDao.clearAll()
    }

    private suspend fun <T> withAutoReLogin(block: suspend () -> T): T {
        return try {
            block()
        } catch (_: SessionExpiredException) {
            val (username, password) = localDataSource.getCredentials() ?: error("Crendential tidak ditemukan")

            internetDataSource.loginStatus(username, password)
            block()
        } catch (_: AccountProblemException) {
            localDataSource.clearCredentials()
            _isLoggedIn.value = false
            error("NIM atau password telah berubah")
        }
    }

    suspend fun checkLoginStatus(nim: String, pwd: String) = runCatching {
        internetDataSource.loginStatus(nim, pwd)
        _isLoggedIn.value = true
    }

    suspend fun login(nim: String, pwd: String) = runCatching {
        internetDataSource.loginStatus(nim, pwd)
        localDataSource.saveCredentials(nim, pwd)
        syncLmsApp()
        _isLoggedIn.value = true
    }

    suspend fun syncAll() = runCatching { withAutoReLogin { syncLmsApp() } }

    private suspend fun syncLmsApp() = coroutineScope {
        val data = internetDataSource.fetchInitialData()

        val allContents = data.allMeetings.flatMap { it.meetings }.map { (_, meetingUrl) ->
            async {
                val contents = internetDataSource.fetchContents(meetingUrl)
                contents.map { it.toEntity(meetingUrl) }
            }
        }.awaitAll().flatten()

        val allAssignments = allContents.filter { it.type == "fa-suitcase" }.map { (meetingUrl, _, _, contentUrl) ->
            async {
                internetDataSource.fetchAssignments(contentUrl).toEntity(meetingUrl)
            }
        }

        lmsDatabase.withTransaction {
            studentDao.save(data.student.toEntity())
            courseDao.save(data.courses.map { it.toEntity() })
            meetingDao.save(data.allMeetings.flatMap { it.toMeetingEntities() })
            attendanceDao.save(data.allPresences.flatMap { it.toAttendanceEntities() })
            contentDao.save(allContents)
            assignmentDao.save(allAssignments.awaitAll())
        }
    }

    suspend fun executeAttendance(fileUrl: String) = runCatching {
        withAutoReLogin { internetDataSource.executeAttendance(fileUrl) }
    }

    suspend fun downloadFile(
        fileUrl: String,
        onProgress: (fileName: String, progress: Float) -> Unit
    ) = runCatching {
        withAutoReLogin {
            val response = internetDataSource.downloadFile(fileUrl)
            if (response.status.value !in 200..299) {
                error("File tidak ada di server (${response.status.value})")
            }

            storageDataSource.saveToDownloads(response, onProgress)
        }
    }

    suspend fun uploadTask(
        uri: Uri,
        taskUrl: String,
        onProgress: (fileName: String, progress: Float) -> Unit
    ) = runCatching {
        withAutoReLogin {
            val fileSource = storageDataSource.openFileStream(uri)
            if (fileSource.size > 20 * 1024 * 1024) error("Ukuran file melebihi 20MB")

            fileSource.stream.use {
                internetDataSource.uploadSubmission(
                    fileSource.name,
                    fileSource.size,
                    it,
                    taskUrl,
                    onProgress
                )
            }
        }
    }
}
