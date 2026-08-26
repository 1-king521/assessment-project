package com.acme.assessment.hr

import com.acme.assessment.auth.AuthenticationService
import com.acme.assessment.domain.AssessmentTask
import com.acme.assessment.domain.AssignmentStatus
import com.acme.assessment.domain.OperationLog
import com.acme.assessment.domain.TaskStatus
import com.acme.assessment.repository.AssessmentAnswerRepository
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentFileRepository
import com.acme.assessment.repository.AssessmentReviewRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.JobPositionRepository
import com.acme.assessment.repository.OperationLogRepository
import com.acme.assessment.repository.UserRepository
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class TaskManagementService(
    private val authenticationService: AuthenticationService,
    private val taskRepository: AssessmentTaskRepository,
    private val assignmentRepository: AssessmentAssignmentRepository,
    private val answerRepository: AssessmentAnswerRepository,
    private val fileRepository: AssessmentFileRepository,
    private val reviewRepository: AssessmentReviewRepository,
    private val positionRepository: JobPositionRepository,
    private val versionRepository: AssessmentTemplateVersionRepository,
    private val userRepository: UserRepository,
    private val operationLogRepository: OperationLogRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(page: Int, pageSize: Int, status: TaskStatus?, keyword: String?): TaskPageResponse {
        validatePage(page, pageSize)
        val tasks = visibleTasks()
            .asSequence()
            .filter { status == null || it.status == status }
            .filter { matches(it, keyword) }
            .sortedByDescending { it.createdAt }
            .toList()
        val from = (page - 1) * pageSize
        val items = if (from >= tasks.size) emptyList() else tasks.subList(from, minOf(from + pageSize, tasks.size))
        return TaskPageResponse(page, pageSize, tasks.size, items.map(::summary))
    }

    @Transactional(readOnly = true)
    fun statistics(): TaskStatisticsResponse {
        val tasks = visibleTasks()
        val counts = TaskStatus.entries.associateWith { status -> tasks.count { it.status == status } }
        return TaskStatisticsResponse(tasks.size, counts)
    }

    @Transactional(readOnly = true)
    fun detail(taskId: Long): TaskDetailResponse {
        val task = loadVisible(taskId)
        val id = requireNotNull(task.id)
        val position = positionRepository.findById(task.positionId).orElseThrow { NotFoundException("招聘岗位") }
        val version = versionRepository.findById(task.templateVersionId).orElseThrow { NotFoundException("模板版本") }
        val assignments = assignmentRepository.findAllByTaskId(id)
        val users = userRepository.findAllByIdIn(assignments.map { it.reviewerUserId }).associateBy { it.id }
        return TaskDetailResponse(
            id, task.taskNo, task.candidateName, task.candidatePhone, task.candidateEmail, task.candidateSource,
            PositionBriefResponse(requireNotNull(position.id), position.positionCode, position.positionName),
            TemplateVersionBriefResponse(requireNotNull(version.id), version.templateId, version.versionNo, version.versionStatus.name, version.schemaJson),
            task.status, task.deadline, task.sentAt, task.openedAt, task.submittedAt, task.reviewStartedAt,
            task.reviewedAt, task.revokedAt, task.archivedAt,
            answerRepository.findAllByTaskId(id).map { TaskAnswerResponse(requireNotNull(it.id), it.questionId, it.answerJson, it.draftVersion, it.submittedAt) },
            fileRepository.findAllByTaskId(id).filter { it.uploadStatus.name != "DELETED" }.map {
                TaskFileResponse(requireNotNull(it.id), it.questionId, it.fileName, it.contentType, it.sizeBytes, it.uploadStatus.name, it.checksum)
            },
            assignments.map { assignment ->
                val review = reviewRepository.findByAssignmentId(requireNotNull(assignment.id))
                TaskAssignmentResponse(
                    requireNotNull(assignment.id), assignment.reviewerUserId, users[assignment.reviewerUserId]?.realName ?: "未知用户",
                    assignment.status, assignment.assignedAt, assignment.startedAt, assignment.completedAt,
                    review?.conclusion, review?.reason, review?.score, review?.submittedAt,
                )
            },
            operationLogRepository.findAll().filter { it.taskId == id }.sortedBy { it.createdAt }.map {
                TaskOperationLogResponse(requireNotNull(it.id), it.operatorType, it.operatorId, it.action, it.fromStatus, it.toStatus, it.detailJson, it.createdAt)
            },
        )
    }

    @Transactional
    fun revoke(taskId: Long): TaskActionResponse = changeStatus(taskId, "TASK_REVOKED", setOf(TaskStatus.DRAFT, TaskStatus.SENT, TaskStatus.OPENED, TaskStatus.IN_PROGRESS)) { task, now ->
        task.status = TaskStatus.REVOKED
        task.revokedAt = now
    }

    @Transactional
    fun extend(taskId: Long, request: ExtendTaskRequest): TaskActionResponse {
        val task = loadVisible(taskId)
        val now = clock.instant()
        if (!request.deadline.isAfter(now)) throw ConflictException("INVALID_DEADLINE", "截止时间必须晚于当前时间")
        if (task.status in setOf(TaskStatus.REVOKED, TaskStatus.ARCHIVED, TaskStatus.REVIEWED)) {
            throw ConflictException("INVALID_TASK_STATUS", "当前状态不允许延期")
        }
        task.deadline = request.deadline
        task.updatedAt = now
        operationLogRepository.save(OperationLog(taskId = task.id, operatorId = authenticationService.currentUser().id, action = "TASK_EXTENDED", detailJson = "{\"deadline\":\"${request.deadline}\"}", createdAt = now))
        return TaskActionResponse(requireNotNull(task.id), task.taskNo, task.status, task.deadline, now)
    }

    @Transactional
    fun archive(taskId: Long): TaskActionResponse = changeStatus(taskId, "TASK_ARCHIVED", setOf(TaskStatus.REVIEWED, TaskStatus.REVOKED, TaskStatus.EXPIRED)) { task, now ->
        task.status = TaskStatus.ARCHIVED
        task.archivedAt = now
    }

    private fun changeStatus(taskId: Long, action: String, allowed: Set<TaskStatus>, mutate: (AssessmentTask, java.time.Instant) -> Unit): TaskActionResponse {
        val task = loadVisible(taskId)
        if (task.status !in allowed) throw ConflictException("INVALID_TASK_STATUS", "当前状态不允许执行该操作")
        val actor = authenticationService.currentUser()
        val oldStatus = task.status
        val now = clock.instant()
        mutate(task, now)
        task.updatedAt = now
        operationLogRepository.save(OperationLog(taskId = task.id, operatorId = actor.id, action = action, fromStatus = oldStatus.name, toStatus = task.status.name, createdAt = now))
        return TaskActionResponse(requireNotNull(task.id), task.taskNo, task.status, task.deadline, now)
    }

    private fun summary(task: AssessmentTask): TaskSummaryResponse {
        val id = requireNotNull(task.id)
        val position = positionRepository.findById(task.positionId).orElse(null)
        val hr = userRepository.findById(task.hrUserId).orElse(null)
        val assignments = assignmentRepository.findAllByTaskId(id)
        return TaskSummaryResponse(id, task.taskNo, task.candidateName, task.candidatePhone, task.candidateEmail, position?.positionName ?: "未知岗位", hr?.realName ?: "未知用户", task.status, task.deadline, task.submittedAt, task.reviewedAt, assignments.size, assignments.count { it.status == AssignmentStatus.COMPLETED })
    }

    private fun visibleTasks(): List<AssessmentTask> {
        val actor = authenticationService.currentUser()
        return if (actor.isAnyRole("HR_MANAGER", "ADMIN")) taskRepository.findAll() else taskRepository.findAll().filter { it.hrUserId == actor.id }
    }

    private fun loadVisible(taskId: Long): AssessmentTask = visibleTasks().firstOrNull { it.id == taskId } ?: throw NotFoundException("测评任务")

    private fun matches(task: AssessmentTask, keyword: String?): Boolean {
        val value = keyword?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return listOf(task.taskNo, task.candidateName, task.candidatePhone, task.candidateEmail).filterNotNull().any { it.contains(value, ignoreCase = true) }
    }

    private fun validatePage(page: Int, pageSize: Int) {
        if (page < 1 || pageSize !in 1..100) throw BusinessException("INVALID_PAGE", "page 必须从1开始，pageSize范围为1到100", HttpStatus.BAD_REQUEST)
    }
}
