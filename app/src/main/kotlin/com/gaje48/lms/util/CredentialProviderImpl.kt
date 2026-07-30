package com.gaje48.lms.util

import com.gaje48.lms.data.LocalDataSource
import uniffi.lms_rust.CredentialProvider
import uniffi.lms_rust.Credentials

class CredentialProviderImpl(private val localDataSource: LocalDataSource) : CredentialProvider {
    override suspend fun getCredentials(): Credentials? {
        val cred = localDataSource.getCredentials() ?: return null
        return Credentials(cred.first, cred.second)
    }
}
