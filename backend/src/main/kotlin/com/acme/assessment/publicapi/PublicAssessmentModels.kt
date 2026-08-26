package com.acme.assessment.publicapi

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

data class PublicAssessmentResponse(
    val taskNo: String,
    val candidateName: String,
    val candidatePhoneRequired: Boolean,
    val positionId: Long,
    val positionName: String,
    val deadline: Instant,
    val status: String,
    val templateVersionId: Long,
    val schemaJson: String,
    val answers: List<AnswerResponse>,
    val files: List<FileResponse>,
)

data class AnswerResponse(
    val questionId: String,
    val answerJson: String,
    val draftVersion: Int,
)

data class FileResponse(
    val id: Long,
    val questionId: String,
    val fileName: String,
    val sizeBytes: Long,
    val uploadStatus: String,
)

data class SaveDraftRequest(
    @field:PositiveOrZero(message = "草稿版本不能为负数")
    val draftVersion: Int? = null,
    @field:Valid
    val answers: List<DraftAnswerRequest>,
)

data class DraftAnswerRequest(
    @field:NotBlank(message = "题目 ID 不能为空")
    val questionId: String,
    @field:NotBlank(message = "答案 JSON 不能为空")
    val answerJson: String,
)

data class SaveDraftResponse(
    val taskNo: String,
    val draftVersion: Int,
    val status: String,
    val savedAt: Instant,
)

data class SubmitAssessmentRequest(
    @field:NotBlank(message = "幂等键不能为空")
    val idempotencyKey: String,
    @field:NotBlank(message = "提交确认不能为空")
    val confirm: String,
)

data class SubmitAssessmentResponse(
    val taskNo: String,
    val status: String,
    val submittedAt: Instant,
)
