package com.acme.assessment.dto

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

data class PublicAssessmentOverviewResponse(
    val taskNo: String,
    val candidateName: String,
    val positionId: Long,
    val positionName: String,
    val deadline: Instant,
    val status: String,
    val abandonmentSource: AbandonmentSource? = null,
    val abandonmentReason: String? = null,
    val abandonedAt: Instant? = null,
)

data class PublicAssessmentResponse(
    val taskNo: String,
    val candidateName: String,
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

data class AbandonAssessmentRequest(
    @field:NotBlank(message = "幂等键不能为空")
    val idempotencyKey: String,
    @field:NotBlank(message = "请填写放弃原因")
    @field:Size(min = 5, max = 500, message = "放弃原因须为5至500个字")
    val reason: String,
)

data class AbandonAssessmentResponse(
    val taskNo: String,
    val status: String,
    val conclusion: ReviewConclusion,
    val abandonmentSource: AbandonmentSource,
    val reason: String,
    val abandonedAt: Instant,
)
