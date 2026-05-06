package com.gaje48.lms.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StudentEntity::class,
        CourseEntity::class,
        MeetingEntity::class,
        ContentEntity::class,
        AssignmentEntity::class,
        AttendanceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LmsDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun courseDao(): CourseDao
    abstract fun meetingDao(): MeetingDao
    abstract fun meetingContentDao(): ContentDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun attendanceDao(): AttendanceDao
}
