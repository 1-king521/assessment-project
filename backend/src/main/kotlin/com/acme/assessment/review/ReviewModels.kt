package com.acme.assessment.review

import com.acme.assessment.domain.AssignmentStatus
import com.acme.assessment.domain.ReviewConclusion
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant

data class ReviewAssignmentResponse(
    val assignmentId: Long,
    val taskId: Long,
    val taskNo: String,
    val candidateName: String,
    val positionName: String,
    val status: AssignmentStatus,
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

data class ReviewResultResponse(
    val conclusion: ReviewConclusion,
    val reason: String?,
    val score: BigDecimal?,
    val submittedAt: Instant,
)

data class SubmitReviewRequest(
    val conclusion: ReviewConclusion,
    val reason: String? = null,
    @field:DecimalMin(value = "0.0", message = "分数不能小于0")
    @field:DecimalMax(value = "100.0", message = "分数不能大于100")
    val score: BigDecimal? = null,
)

data class SubmitReviewResponse(
    val assignmentId: Long,
    val assignmentStatus: AssignmentStatus,
    val taskStatus: String,
    val conclusion: ReviewConclusion,
    val submittedAt: Instant,
)

