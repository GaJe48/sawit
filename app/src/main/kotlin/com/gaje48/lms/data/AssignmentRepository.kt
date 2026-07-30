package com.gaje48.lms.data

import com.gaje48.lms.data.db.AssignmentDao
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AssignmentRepository(
    private val internetDataSource: uniffi.lms_rust.InternetDataSource,
    private val assignmentDao: AssignmentDao,
) {
    val unsubmittedAssignmentCounts = assignmentDao.observeUnsubmittedCounts().map { list ->
        list.associate { it.courseCode to it.count }
    }

    fun observeAssignmentScreenDatas(courseCode: String) = assignmentDao.observeAssignmentScreenDatas(courseCode)

    suspend fun syncAssignmentStatus(assignmentUrl: String): Result<Unit, Throwable> = coroutineBinding {
        withContext(Dispatchers.IO) {
            val assignment = runSuspendCatching {
                internetDataSource.fetchAssignment(assignmentUrl)
            }.bind()

            assignmentDao.updateStatus(
                assignmentUrl = assignment.url,
                isSubmitted = assignment.isSubmitted,
                isOverdue = assignment.isOverdue,
                submissionFileUrl = assignment.answerUrl,
            )
        }
    }
}
