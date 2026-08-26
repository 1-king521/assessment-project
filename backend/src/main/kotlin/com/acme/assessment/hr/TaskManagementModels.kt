package com.acme.assessment.hr

import com.acme.assessment.domain.AssignmentStatus
import com.acme.assessment.domain.ReviewConclusion
import com.acme.assessment.domain.TaskStatus
import jakarta.validation.constraints.Future
import java.math.BigDecimal
import java.time.Instant

data class TaskPageResponse(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val items: List<TaskSummaryResponse>,
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

data class TaskAssignmentResponse(
    val id: Long,
    val reviewerUserId: Long,
    val reviewerUserName: String,
    val status: AssignmentStatus,
    val assignedAt: Instant,
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

data class TaskActionResponse(
    val id: Long,
    val taskNo: String,
    val status: TaskStatus,
    val deadline: Instant,
    val operatedAt: Instant,
)
