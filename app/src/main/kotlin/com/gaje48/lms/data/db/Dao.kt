package com.gaje48.lms.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gaje48.lms.model.AssignmentScreenData
import com.gaje48.lms.model.AttendanceVmData
import com.gaje48.lms.model.ContentVmData
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM student")
    fun observe(): Flow<StudentEntity?>

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
    suspend fun upsert(courses: List<CourseEntity>)

    @Query("DELETE FROM course WHERE courseCode NOT IN (:primaryKeys)")
    suspend fun sync(primaryKeys: List<String>)

    @Transaction
    suspend fun save(courses: List<CourseEntity>) {
        upsert(courses)
        sync(courses.map { it.courseCode })
    }

    @Query("DELETE FROM course")
    suspend fun clearAll()
}

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meeting WHERE courseCode = :foreignKey")
    fun observeByCourse(foreignKey: String): Flow<List<MeetingEntity>>

    @Upsert
    suspend fun save(meetings: List<MeetingEntity>)
}

@Dao
interface ContentDao {
    @Query("SELECT * FROM content WHERE meetingUrl = :foreignKey")
    fun observeByMeeting(foreignKey: String): Flow<List<ContentEntity>>

    @Query(
        """
        SELECT 
            c.type,
            c.title,
            c.contentUrl,
            m.meetingNumber, 
            cr.courseName 
        FROM content c
        INNER JOIN meeting m ON c.meetingUrl = m.meetingUrl
        INNER JOIN course cr ON m.courseCode = cr.courseCode
        WHERE c.meetingUrl = :meetingUrl
    """,
    )
    fun observeContentVmDatas(meetingUrl: String): Flow<List<ContentVmData>>

    @Query(
        """
    SELECT m.meetingNumber, c.contentUrl 
    FROM meeting m 
    INNER JOIN content c ON m.meetingUrl = c.meetingUrl 
    WHERE m.courseCode = :courseCode 
    AND c.contentUrl LIKE 'https://lms.unindra.ac.id%'
    """,
    )
    fun observeAttendanceVmDatas(courseCode: String): Flow<List<AttendanceVmData>>

    @Upsert
    suspend fun save(contents: List<ContentEntity>)
}

@Dao
interface AssignmentDao {
    @Query(
        """
        SELECT
            a.assignmentUrl,
            a.meetingUrl,
            m.meetingNumber,
            a.description, 
            a.assignmentFileUrl, 
            a.submissionFileUrl, 
            a.deadline, 
            a.isSubmitted, 
            a.isOverdue
        FROM assignment a
        JOIN meeting m ON a.meetingUrl = m.meetingUrl
        WHERE m.courseCode = :courseCode
    """,
    )
    fun observeAssignmentScreenDatas(courseCode: String): Flow<List<AssignmentScreenData>>

    @Upsert
    suspend fun save(assignment: AssignmentEntity)

    @Upsert
    suspend fun saveAll(assignments: List<AssignmentEntity>)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance")
    fun observeAll(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE courseCode = :foreignKey")
    fun observeByCourse(foreignKey: String): Flow<List<AttendanceEntity>>

    @Upsert
    suspend fun save(attendances: List<AttendanceEntity>)
}
