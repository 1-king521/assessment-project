package com.acme.assessment.dto

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.ReviewConclusion
import java.time.Instant
import org.springframework.core.io.Resource

data class ReviewAssignmentResponse(
    val assignmentId: Long,
    val taskId: Long,
    val taskNo: String,
    val candidateName: String,
    val positionName: String,
    val status: AssignmentStatus,
    val reviewDueAt: Instant?,
    val submittedAt: Instant?,
    val completedAt: Instant?,
    val conclusion: ReviewConclusion?,
)

data class ReviewDetailResponse(
    val assignmentId: Long,
    val taskId: Long,
    val taskNo: String,
    val candidateName: String,
    val candidateEmail: String?,
    val positionName: String,
    val taskStatus: String,
    val assignmentStatus: AssignmentStatus,
    val reviewDueAt: Instant?,
    val schemaJson: String,
    val answers: List<ReviewAnswerResponse>,
    val files: List<ReviewFileResponse>,
    val review: ReviewResultResponse?,
)

data class ReviewAnswerResponse(
    val questionId: String,
    val answerJson: String,
    val submittedAt: Instant?,
)

data class ReviewFileResponse(
    val id: Long,
    val questionId: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadStatus: String,
)

data class ReviewFileContent(
    val fileName: String,
    val contentType: String,
    val resource: Resource,
)

data class ReviewResultResponse(
    val conclusion: ReviewConclusion,
    val reason: String?,
    val submittedAt: Instant,
)

data class SubmitReviewRequest(
    val conclusion: ReviewConclusion,
    val reason: String? = null,
)

data class SubmitReviewResponse(
    val assignmentId: Long,
    val assignmentStatus: AssignmentStatus,
    val taskStatus: String,
    val conclusion: ReviewConclusion,
    val submittedAt: Instant,
)
