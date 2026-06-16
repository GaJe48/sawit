package com.gaje48.lms.data

import com.gaje48.lms.data.db.CourseDao
import com.gaje48.lms.data.db.StudentDao
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val internetDataSource: uniffi.lms_rust.InternetDataSource,
    private val localDataSource: LocalDataSource,
    private val studentDao: StudentDao,
    private val courseDao: CourseDao,
) {
    val isLoggedIn = localDataSource.credentials.map { it != null }

    suspend fun savedCredential() = localDataSource.getCredentials()

    suspend fun logout() {
        localDataSource.clearCredentials()
        localDataSource.clearLastSyncTime()
        studentDao.clear()
        courseDao.clearAll()
    }

    suspend fun checkLoginStatus(
        nim: String,
        pwd: String,
    ) = runCatching { internetDataSource.cookieRenewed(nim, pwd) }.onFailure { exception ->
        if (exception is uniffi.lms_rust.LmsException.CredentialException) {
            logout()
        }
    }

    suspend fun login(
        nim: String,
        pwd: String,
    ) = runCatching { internetDataSource.cookieRenewed(nim, pwd) }

    suspend fun requestResetPassword(email: String) = runCatching { internetDataSource.requestResetPassword(email) }

    suspend fun saveCredentials(
        nim: String,
        pwd: String,
    ) {
        localDataSource.saveCredentials(nim, pwd)
    }
}
