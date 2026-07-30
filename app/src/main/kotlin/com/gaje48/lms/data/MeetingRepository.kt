package com.gaje48.lms.data

import com.gaje48.lms.data.db.ContentDao
import com.gaje48.lms.data.db.MeetingDao
import com.gaje48.lms.data.db.toDomain
import kotlinx.coroutines.flow.map

class MeetingRepository(
    private val meetingDao: MeetingDao,
    private val contentDao: ContentDao,
) {
    fun observeMeetings(courseCode: String) =
        meetingDao.observeByCourse(courseCode).map { entities ->
            entities.map { it.toDomain() }
        }

    fun observeContentVmDatasByCourse(courseCode: String) = contentDao.observeContentVmDatasByCourse(courseCode)
}
