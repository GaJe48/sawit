package com.gaje48.lms.data.db

import com.gaje48.lms.model.Course
import com.gaje48.lms.model.Meeting
import com.gaje48.lms.model.Student

fun StudentEntity.toDomain() =
    Student(
        npm = npm,
        studentName = studentName,
        studyProgram = studyProgram,
        classCode = classCode,
        studentProfilePictureUrl = studentProfilePictureUrl,
    )

fun CourseEntity.toDomain() =
    Course(
        courseCode = courseCode,
        courseName = courseName,
        day = day,
        clock = clock,
        room = room,
        lecturerName = lecturerName,
        lecturerPhoneNumber = lecturerPhoneNumber,
        lecturerProfilePictureUrl = lecturerProfilePictureUrl,
    )

fun MeetingEntity.toDomain() =
    Meeting(
        meetingNumber = meetingNumber.toUByte(),
        meetingUrl = meetingUrl,
    )

fun uniffi.lms_rust.Student.toEntity() =
    StudentEntity(
        npm,
        name,
        studyProgram,
        className,
        profilePictureUrl,
    )

fun uniffi.lms_rust.Course.toEntity() =
    CourseEntity(
        code,
        name,
        day,
        clock,
        room,
        lecturerName,
        lecturerPhoneNumber,
        lecturerProfilePictureUrl,
    )

fun uniffi.lms_rust.MeetingEntity.toEntity() =
    MeetingEntity(
        url,
        courseCode,
        number.toByte(),
    )

fun uniffi.lms_rust.AttendanceEntity.toEntity() =
    AttendanceEntity(
        courseCode,
        index.toByte(),
        isAttended,
    )

fun uniffi.lms_rust.ContentEntity.toEntity() =
    ContentEntity(
        meetingUrl,
        contentType,
        title,
        url,
    )

fun uniffi.lms_rust.AssignmentEntity.toEntity() =
    AssignmentEntity(
        url,
        meetingUrl,
        message,
        questionUrl,
        answerUrl,
        deadline,
        isSubmitted,
        isOverdue,
    )
