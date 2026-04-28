package com.gaje48.lms.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM student")
    fun observe(): Flow<StudentEntity>

    @Upsert
    suspend fun save(student: StudentEntity)

    @Query("DELETE FROM student")
    suspend fun clear()
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM course")
    fun observeAll(): Flow<List<CourseEntity>>

    @Upsert
    suspend fun upsertAll(courses: List<CourseEntity>)

    @Query("DELETE FROM course WHERE courseCode NOT IN (:courseCodes)")
    suspend fun clearByCourseCodes(courseCodes: List<String>)

    @Transaction
    suspend fun saveAll(courses: List<CourseEntity>) {
        upsertAll(courses)

        val courseCodes = courses.map { it.courseCode }
        clearByCourseCodes(courseCodes)
    }

    @Query("DELETE FROM course")
    suspend fun clearAll()
}

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meeting WHERE courseCode = :courseCode")
    fun observeAll(courseCode: String): Flow<List<MeetingEntity>>

    @Upsert
    suspend fun upsertAll(meetings: List<MeetingEntity>)

    @Query("DELETE FROM meeting WHERE meetingUrl NOT IN (:meetingUrls)")
    suspend fun clearByMeetingUrls(meetingUrls: List<String>)

    @Transaction
    suspend fun saveAll(meetings: List<MeetingEntity>) {
        upsertAll(meetings)

        val meetingUrls = meetings.map { it.meetingUrl }
        clearByMeetingUrls(meetingUrls)
    }
}

@Dao
interface MeetingContentDao {
    @Query("SELECT * FROM meeting_content WHERE meetingId = :meetingId")
    fun observeAll(meetingId: Int): Flow<List<MeetingContentEntity>>

    @Upsert
    suspend fun upsertAll(contents: List<MeetingContentEntity>)

    @Query("DELETE FROM meeting_content WHERE contentUrl NOT IN (:contentUrls)")
    suspend fun clearByContentUrls(contentUrls: List<String>)

    @Transaction
    suspend fun saveAll(contents: List<MeetingContentEntity>) {
        upsertAll(contents)

        val contentUrls = contents.map { it.contentUrl }
        clearByContentUrls(contentUrls)
    }
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignment WHERE meetingId = :meetingId")
    fun observe(meetingId: Int): Flow<List<AssignmentEntity>>

    @Upsert
    suspend fun upsertAll(assignments: List<AssignmentEntity>)

    @Query("DELETE FROM assignment WHERE assignmentUrl NOT IN (:assignmentUrls)")
    suspend fun clearByAssignmentUrls(assignmentUrls: List<String>)

    @Transaction
    suspend fun saveAll(assignments: List<AssignmentEntity>) {
        upsertAll(assignments)

        val assignmentUrls = assignments.map { it.assignmentUrl }
        clearByAssignmentUrls(assignmentUrls)
    }
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE courseCode = :courseCode")
    fun observeAll(courseCode: String): Flow<List<AttendanceEntity>>

    @Upsert
    suspend fun upsertAll(attendances: List<AttendanceEntity>)

    @Query("DELETE FROM attendance WHERE courseCode NOT IN (:courseCodes)")
    suspend fun clearByCourseCodes(courseCodes: List<String>)

    @Transaction
    suspend fun saveAll(attendances: List<AttendanceEntity>) {
        upsertAll(attendances)

        val courseCodes = attendances.map { it.courseCode }
        clearByCourseCodes(courseCodes)
    }
}
