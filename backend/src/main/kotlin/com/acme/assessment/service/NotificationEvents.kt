package com.acme.assessment.service

import java.time.Instant

data class CandidateSubmittedEvent(val taskId: Long, val hrUserId: Long)

data class ReviewersAssignedEvent(val taskId: Long, val reviewerUserIds: Set<Long>, val reviewDueAt: Instant? = null)

data class ReviewOverdueEvent(val taskId: Long, val reviewerUserId: Long, val assignmentId: Long, val reviewDueAt: Instant)

data class AllReviewsCompletedEvent(val taskId: Long, val hrUserId: Long, val reviewerCount: Int)
