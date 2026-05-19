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
import com.gaje48.lms.data.db.toDomain
import com.gaje48.lms.data.db.toEntity
import com.gaje48.lms.model.AccountProblemException
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LmsRepository(
    private val internetDataSource: uniffi.lms_rust.InternetDataSource,
    private val storageDataSource: StorageDataSource,
    private val localDataSource: LocalDataSource,
    private val lmsDatabase: LmsDatabase,
    private val studentDao: StudentDao,
    private val courseDao: CourseDao,
    private val meetingDao: MeetingDao,
    private val contentDao: ContentDao,
    private val assignmentDao: AssignmentDao,
    private val attendanceDao: AttendanceDao,
) {
    val student = studentDao.observe().map { it.toDomain() }

    val courses =
        courseDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    val allAttendances =
        attendanceDao.observeAll().map { entities ->
            entities.groupBy { it.courseCode }.map { (courseCode, attendances) ->
                AttendancesByCourse(courseCode, attendances.map { it.isAttended })
            }
        }

    fun observeMeetings(courseCode: String) =
        meetingDao.observeByCourse(courseCode).map { entities ->
            entities.map { it.toDomain() }
        }

    fun observeAttendances(courseCode: String) =
        attendanceDao.observeByCourse(courseCode).map { entities ->
            entities.map { it.isAttended }
        }

    fun observeContentVmDatas(meetingUrl: String) = contentDao.observeContentVmDatas(meetingUrl)

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

    private suspend fun <T> withAutoReLogin(block: suspend () -> T): T =
        try {
            block()
        } catch (_: SessionExpiredException) {
            val (username, password) =
                localDataSource.getCredentials()
                    ?: error("Crendential tidak ditemukan")

            internetDataSource.cookieRenewed(username, password)
            block()
        } catch (_: AccountProblemException) {
            localDataSource.clearCredentials()
            _isLoggedIn.value = false
            error("NIM atau password telah berubah")
        }

    suspend fun checkLoginStatus(
        nim: String,
        pwd: String,
    ) = runCatching {
        internetDataSource.cookieRenewed(nim, pwd)
        _isLoggedIn.value = true
    }

    suspend fun login(
        nim: String,
        pwd: String,
    ) = runCatching {
        internetDataSource.cookieRenewed(nim, pwd)
        localDataSource.saveCredentials(nim, pwd)
        syncLmsApp()
        _isLoggedIn.value = true
    }

    suspend fun syncAll() = runCatching { withAutoReLogin { syncLmsApp() } }

    private suspend fun syncLmsApp() =
        withContext(Dispatchers.IO) {
            val lmsEntity = internetDataSource.fetchAll()

            lmsDatabase.withTransaction {
                studentDao.save(lmsEntity.student.toEntity())
                courseDao.save(lmsEntity.courses.map { it.toEntity() })
                meetingDao.save(lmsEntity.meetings.map { it.toEntity() })
                attendanceDao.save(lmsEntity.attendances.map { it.toEntity() })
                contentDao.save(lmsEntity.contents.map { it.toEntity() })
                assignmentDao.save(lmsEntity.assignments.map { it.toEntity() })
            }
        }

    suspend fun executeAttendances(urls: List<String>) =
        runCatching {
            withAutoReLogin { internetDataSource.executeAttendances(urls) }
        }

    suspend fun downloadFile(
        fileUrl: String,
        rawFileName: String,
        onProgress: (fileName: String, progress: Float) -> Unit,
    ) = runCatching {
        withAutoReLogin {
            internetDataSource.downloadFile(
                fileUrl,
                rawFileName,
                object : uniffi.lms_rust.DownloadCallback {
                    override suspend fun onStart(fileName: String) = storageDataSource.createDownloadFd(fileName)

                    override fun onProgress(
                        fileName: String,
                        progress: Float,
                    ) {
                        onProgress(fileName, progress)
                    }
                },
            )
        }
    }

    suspend fun uploadTask(
        uri: Uri,
        taskUrl: String,
        onProgress: (fileName: String, progress: Float) -> Unit,
    ) = runCatching {
        val (fileName, fileSize, fd) = storageDataSource.openFileForUpload(uri)
        if (fileSize > 20 * 1024 * 1024) error("Ukuran berkas melebihi 20MB")

        withAutoReLogin {
            internetDataSource.uploadSubmission(
                taskUrl,
                fileName,
                fileSize.toULong(),
                fd,
                object : uniffi.lms_rust.UploadCallback {
                    override fun onProgress(
                        fileName: String,
                        progress: Float,
                    ) {
                        onProgress(fileName, progress)
                    }
                },
            )
        }
    }
}
