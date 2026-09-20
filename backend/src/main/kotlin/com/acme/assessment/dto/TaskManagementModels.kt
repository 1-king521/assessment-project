package com.acme.assessment.dto

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.ReviewConclusion
import com.acme.assessment.entity.TaskStatus
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
import org.springframework.core.io.Resource

data class TaskPageResponse(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val items: List<TaskSummaryResponse>,
)

data class AssessmentRecordPageResponse(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val items: List<AssessmentRecordSummaryResponse>,
)

data class AssessmentRecordSummaryResponse(
    val id: Long,
    val taskNo: String,
    val candidateName: String,
    val candidatePhone: String?,
    val candidateEmail: String?,
    val positionId: Long,
    val positionName: String,
    val templateVersionNo: Int,
    val status: TaskStatus,
    val submittedAt: Instant?,
    val reviewedAt: Instant?,
    val concludedAt: Instant?,
    val archivedAt: Instant?,
    val conclusions: List<ReviewConclusion>,
)

data class TaskSummaryResponse(
    val id: Long,
    val taskNo: String,
    val candidateName: String,
    val candidatePhone: String?,
    val candidateEmail: String?,
    val positionName: String,
    val hrUserName: String,
    val status: TaskStatus,
    val deadline: Instant,
    val sentAt: Instant?,
    val submittedAt: Instant?,
    val reviewedAt: Instant?,
    val assignmentCount: Int,
    val completedAssignmentCount: Int,
)

data class TaskStatisticsResponse(
    val total: Int,
    val byStatus: Map<TaskStatus, Int>,
)

data class TaskDetailResponse(
    val id: Long,
    val taskNo: String,
    val candidateName: String,
    val candidatePhone: String?,
    val candidateEmail: String?,
    val candidateSource: String?,
    val position: PositionBriefResponse,
    val templateVersion: TemplateVersionBriefResponse,
    val status: TaskStatus,
    val deadline: Instant,
    val sentAt: Instant?,
    val openedAt: Instant?,
    val submittedAt: Instant?,
    val reviewStartedAt: Instant?,
    val reviewedAt: Instant?,
    val revokedAt: Instant?,
    val archivedAt: Instant?,
    val finalConclusion: ReviewConclusion?,
    val finalConclusionAt: Instant?,
    val finalConclusionReason: String?,
    val answers: List<TaskAnswerResponse>,
    val files: List<TaskFileResponse>,
    val assignments: List<TaskAssignmentResponse>,
    val operationLogs: List<TaskOperationLogResponse>,
)

data class PositionBriefResponse(val id: Long, val code: String, val name: String)

data class TemplateVersionBriefResponse(
    val id: Long,
    val templateId: Long,
    val versionNo: Int,
    val status: String,
    val schemaJson: String,
)

data class TaskAnswerResponse(
    val id: Long,
    val questionId: String,
    val answerJson: String,
    val draftVersion: Int,
    val submittedAt: Instant?,
)

data class TaskFileResponse(
    val id: Long,
    val questionId: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadStatus: String,
    val checksum: String?,
)

data class TaskFileContent(
    val fileName: String,
    val contentType: String,
    val resource: Resource,
)

data class TaskAssignmentResponse(
    val id: Long,
    val reviewerUserId: Long,
    val reviewerUserName: String,
    val status: AssignmentStatus,
    val assignedAt: Instant,
    val reviewDueAt: Instant?,
    val overdueReminderSentAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val conclusion: ReviewConclusion?,
    val reason: String?,
    val score: BigDecimal?,
    val reviewSubmittedAt: Instant?,
)

data class TaskOperationLogResponse(
    val id: Long,
    val operatorType: String,
    val operatorId: Long?,
    val action: String,
    val fromStatus: String?,
    val toStatus: String?,
    val detailJson: String?,
    val createdAt: Instant,
)

data class ExtendTaskRequest(
    @field:Future(message = "截止时间必须晚于当前时间")
    val deadline: Instant,
)

data class AssignReviewersRequest(
    @field:NotEmpty(message = "至少选择一名评估人员")
    val reviewerUserIds: Set<@Positive(message = "评估人员 ID 必须为正数") Long>,
    @field:Min(value = 1, message = "评估时限不能少于1小时")
    @field:Max(value = 720, message = "评估时限不能超过30天")
    val reviewTimeoutHours: Long = 24,
)

data class TaskActionResponse(
    val id: Long,
    val taskNo: String,
    val status: TaskStatus,
    val deadline: Instant,
    val operatedAt: Instant,
)
