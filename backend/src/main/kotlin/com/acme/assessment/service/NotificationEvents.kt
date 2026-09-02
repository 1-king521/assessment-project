package com.acme.assessment.service

data class CandidateSubmittedEvent(val taskId: Long, val hrUserId: Long)

data class ReviewersAssignedEvent(val taskId: Long, val reviewerUserIds: Set<Long>)
