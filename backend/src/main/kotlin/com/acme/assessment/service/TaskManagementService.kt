package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.service.AuthenticationService
import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.entity.ReviewConclusion
import com.acme.assessment.entity.UserStatus
import com.acme.assessment.service.FileStorageService
import com.acme.assessment.repository.AssessmentAnswerRepository
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentFileRepository
import com.acme.assessment.repository.AssessmentReviewRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.JobPositionRepository
import com.acme.assessment.repository.OperationLogRepository
import com.acme.assessment.repository.UserRepository
import com.acme.assessment.repository.RoleRepository
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.context.ApplicationEventPublisher
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
    private val roleRepository: RoleRepository,
    private val operationLogRepository: OperationLogRepository,
    private val objectMapper: ObjectMapper,
    private val fileStorageService: FileStorageService,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher = ApplicationEventPublisher { },
) {
    @Transactional(readOnly = true)
    fun list(page: Int, pageSize: Int, status: TaskStatus?, keyword: String?): TaskPageResponse {
        validatePage(page, pageSize)
        val actor = authenticationService.currentUser()
        val normalizedKeyword = keyword?.trim().orEmpty()
        val pageable = PageRequest.of(page - 1, pageSize)
        val result = if (actor.isAnyRole("HR_MANAGER", "ADMIN")) {
            taskRepository.searchVisibleForManagers(status, normalizedKeyword, pageable)
        } else {
            taskRepository.searchVisibleForHr(actor.id, status, normalizedKeyword, pageable)
        }
        return TaskPageResponse(page, pageSize, result.totalElements.toInt(), summarize(result.content))
    }

    @Transactional(readOnly = true)
    fun statistics(): TaskStatisticsResponse {
        val actor = authenticationService.currentUser()
        val rows = if (actor.isAnyRole("HR_MANAGER", "ADMIN")) taskRepository.countVisibleByStatusForManagers() else taskRepository.countVisibleByStatusForHr(actor.id)
        val counts = TaskStatus.entries.associateWith { status -> rows.firstOrNull { it.status == status }?.total?.toInt() ?: 0 }
        return TaskStatisticsResponse(counts.values.sum(), counts)
    }

    @Transactional(readOnly = true)
    fun listRecords(
        page: Int,
        pageSize: Int,
        keyword: String?,
        positionId: Long?,
        conclusion: ReviewConclusion?,
    ): AssessmentRecordPageResponse {
        validatePage(page, pageSize)
        val actor = authenticationService.currentUser()
        val result = taskRepository.searchRecords(
            if (actor.isAnyRole("HR_MANAGER", "ADMIN")) null else actor.id,
            positionId, keyword?.trim().orEmpty(), conclusion,
            PageRequest.of(page - 1, pageSize),
        )
        return AssessmentRecordPageResponse(page, pageSize, result.totalElements.toInt(), result.content.map(::recordSummary))
    }

    @Transactional(readOnly = true)
    fun detail(taskId: Long): TaskDetailResponse {
        val task = loadVisible(taskId)
        return buildDetail(task, task.status in CONTENT_STATUSES)
    }

    @Transactional
    fun assignReviewers(taskId: Long, request: AssignReviewersRequest): TaskDetailResponse {
        val task = loadVisibleForUpdate(taskId)
        if (task.status != TaskStatus.SUBMITTED) {
            throw ConflictException("TASK_NOT_READY_FOR_ASSIGNMENT", "只有候选人提交后才能分配评估人员")
        }
        val existing = assignmentRepository.findAllByTaskId(taskId)
        if (existing.any { it.status != AssignmentStatus.PENDING }) {
            throw ConflictException("ASSIGNMENT_LOCKED", "评估已开始，不能再次调整评估人员")
        }
        val reviewerIds = request.reviewerUserIds
        val reviewers = userRepository.findAllByIdIn(reviewerIds)
        if (reviewers.size != reviewerIds.size) throw BusinessException("INVALID_REVIEWER", "存在无效的评估人员")
        val roles = roleRepository.findAllById(reviewers.map { it.roleId }).associateBy { it.id }
        if (reviewers.any { it.status != UserStatus.ACTIVE || roles[it.roleId]?.roleCode != "REVIEWER" }) {
            throw BusinessException("INVALID_REVIEWER", "评估人员必须是启用状态的 REVIEWER 用户")
        }
        if (reviewers.any { it.positionId != task.positionId }) {
            throw BusinessException("REVIEWER_POSITION_MISMATCH", "评估人员必须属于当前任务岗位")
        }
        val now = clock.instant()
        val actor = authenticationService.currentUser()
        existing.filter { it.reviewerUserId !in reviewerIds }.forEach {
            assignmentRepository.delete(it)
            operationLogRepository.save(OperationLog(taskId = taskId, operatorId = actor.id, action = "REVIEWER_REMOVED", detailJson = "{\"reviewerUserId\":${it.reviewerUserId}}", createdAt = now))
        }
        val existingIds = existing.map { it.reviewerUserId }.toSet()
        reviewerIds.filter { it !in existingIds }.forEach { reviewerId ->
            assignmentRepository.save(AssessmentAssignment(taskId = taskId, reviewerUserId = reviewerId, status = AssignmentStatus.PENDING, assignedBy = actor.id, assignedAt = now, createdAt = now, updatedAt = now))
            operationLogRepository.save(OperationLog(taskId = taskId, operatorId = actor.id, action = "REVIEWER_ADDED", detailJson = "{\"reviewerUserId\":$reviewerId}", createdAt = now))
        }
        assignmentRepository.flush()
        operationLogRepository.save(OperationLog(taskId = taskId, operatorId = actor.id, action = "REVIEWERS_ASSIGNED", detailJson = objectMapper.writeValueAsString(mapOf("reviewerUserIds" to reviewerIds)), createdAt = now))
        eventPublisher.publishEvent(ReviewersAssignedEvent(taskId, reviewerIds.filter { it !in existingIds }.toSet()))
        return buildDetail(task, true)
    }

    @Transactional(readOnly = true)
    fun recordDetail(taskId: Long): TaskDetailResponse {
        val task = loadVisible(taskId)
        if (task.status !in RECORD_STATUSES || recordTimestamp(task) == null) throw NotFoundException("测评记录")
        return buildDetail(task, true)
    }

    @Transactional(readOnly = true)
    fun recordFileContent(taskId: Long, fileId: Long): TaskFileContent {
        val task = loadVisible(taskId)
        if (task.status !in CONTENT_STATUSES) throw NotFoundException("测评记录")
        val file = fileStorageService.findForTask(fileId, requireNotNull(task.id))
        if (file.uploadStatus.name != "COMPLETED") throw NotFoundException("测评文件内容")
        return TaskFileContent(file.fileName, file.contentType, fileStorageService.loadContent(file))
    }

    private fun buildDetail(task: AssessmentTask, includeSubmission: Boolean): TaskDetailResponse {
        val id = requireNotNull(task.id)
        val position = positionRepository.findById(task.positionId).orElseThrow { NotFoundException("招聘岗位") }
        val version = versionRepository.findById(task.templateVersionId).orElseThrow { NotFoundException("模板版本") }
        val assignments = assignmentRepository.findAllByTaskId(id)
        val users = userRepository.findAllByIdIn(assignments.map { it.reviewerUserId }).associateBy { it.id }
        return TaskDetailResponse(
            id, task.taskNo, task.candidateName, task.candidatePhone, task.candidateEmail, task.candidateSource,
            PositionBriefResponse(requireNotNull(position.id), position.positionCode, position.positionName),
            TemplateVersionBriefResponse(requireNotNull(version.id), version.templateId, version.versionNo, version.versionStatus.name, if (includeSubmission) version.schemaJson else "{}"),
            task.status, task.deadline, task.sentAt, task.openedAt, task.submittedAt, task.reviewStartedAt,
            task.reviewedAt, task.revokedAt, task.archivedAt,
            task.finalConclusion, task.finalConclusionAt, task.finalConclusionReason,
            //查询该任务下所有候选人答题记录
            if (includeSubmission) answerRepository.findAllByTaskId(id).map { TaskAnswerResponse(requireNotNull(it.id), it.questionId, it.answerJson, it.draftVersion, it.submittedAt) } else emptyList(),
            //查询任务附件，过滤掉已删除的文件
            if (includeSubmission) fileRepository.findAllByTaskId(id).filter { it.uploadStatus.name != "DELETED" }.map {
                TaskFileResponse(requireNotNull(it.id), it.questionId, it.fileName, it.contentType, it.sizeBytes, it.uploadStatus.name, it.checksum)
            } else emptyList(),
            //查询评估分配记录
            if (task.finalConclusion == null) assignments.map { assignment ->
                val review = reviewRepository.findByAssignmentId(requireNotNull(assignment.id))
                TaskAssignmentResponse(
                    requireNotNull(assignment.id), assignment.reviewerUserId, users[assignment.reviewerUserId]?.realName ?: "未知用户",
                    assignment.status, assignment.assignedAt, assignment.startedAt, assignment.completedAt,
                    review?.conclusion, review?.reason, review?.score, review?.submittedAt,
                )
            } else emptyList(),
            operationLogRepository.findAllByTaskIdOrderByCreatedAtAsc(id).map {
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

    private fun summarize(tasks: List<AssessmentTask>): List<TaskSummaryResponse> {
        if (tasks.isEmpty()) return emptyList()
        val ids = tasks.mapNotNull { it.id }
        val positions = positionRepository.findAllByIdIn(tasks.map { it.positionId }).associateBy { it.id }
        val users = userRepository.findAllByIdIn(tasks.map { it.hrUserId }).associateBy { it.id }
        val assignmentsByTask = assignmentRepository.findAllByTaskIdIn(ids).groupBy { it.taskId }
        return tasks.map { task ->
            val assignments = assignmentsByTask[requireNotNull(task.id)].orEmpty()
            TaskSummaryResponse(
                id = requireNotNull(task.id),
                taskNo = task.taskNo,
                candidateName = task.candidateName,
                candidatePhone = task.candidatePhone,
                candidateEmail = task.candidateEmail,
                positionName = positions[task.positionId]?.positionName ?: "未知岗位",
                hrUserName = users[task.hrUserId]?.realName ?: "未知用户",
                status = task.status,
                deadline = task.deadline,
                submittedAt = task.submittedAt,
                reviewedAt = task.reviewedAt,
                assignmentCount = assignments.size,
                completedAssignmentCount = assignments.count { it.status == AssignmentStatus.COMPLETED },
            )
        }
    }

    private fun recordSummary(task: AssessmentTask): AssessmentRecordSummaryResponse {
        val position = positionRepository.findById(task.positionId).orElse(null)
        val version = versionRepository.findById(task.templateVersionId).orElse(null)
        return AssessmentRecordSummaryResponse(
            id = requireNotNull(task.id),
            taskNo = task.taskNo,
            candidateName = task.candidateName,
            candidatePhone = task.candidatePhone,
            candidateEmail = task.candidateEmail,
            positionId = task.positionId,
            positionName = position?.positionName ?: "未知岗位",
            templateVersionNo = version?.versionNo ?: 0,
            status = task.status,
            submittedAt = task.submittedAt,
            reviewedAt = task.reviewedAt,
            concludedAt = recordTimestamp(task),
            archivedAt = task.archivedAt,
            conclusions = conclusions(task),
        )
    }

    private fun conclusions(task: AssessmentTask): List<ReviewConclusion> =
        task.finalConclusion?.let(::listOf)
            ?: assignmentRepository.findAllByTaskId(requireNotNull(task.id))
                .let { assignments ->
                    val reviews = reviewRepository.findAllByAssignmentIdIn(assignments.mapNotNull { it.id })
                        .associateBy { it.assignmentId }
                    assignments.mapNotNull { assignment -> reviews[assignment.id]?.conclusion }
                }

    private fun recordTimestamp(task: AssessmentTask) = task.finalConclusionAt ?: task.reviewedAt

    private fun visibleTasks(): List<AssessmentTask> {
        val actor = authenticationService.currentUser()
        return if (actor.isAnyRole("HR_MANAGER", "ADMIN")) taskRepository.findAll() else taskRepository.findAll().filter { it.hrUserId == actor.id }
    }

    private fun activeTasks(): List<AssessmentTask> = visibleTasks().filter { it.status != TaskStatus.ARCHIVED }

    private fun loadVisible(taskId: Long): AssessmentTask = visibleTasks().firstOrNull { it.id == taskId } ?: throw NotFoundException("测评任务")

    private fun loadVisibleForUpdate(taskId: Long): AssessmentTask {
        val task = taskRepository.findByIdForUpdate(taskId) ?: throw NotFoundException("测评任务")
        val actor = authenticationService.currentUser()
        if (!actor.isAnyRole("HR_MANAGER", "ADMIN") && task.hrUserId != actor.id) throw NotFoundException("测评任务")
        return task
    }

    private fun matches(task: AssessmentTask, keyword: String?): Boolean {
        val value = keyword?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return listOf(task.taskNo, task.candidateName, task.candidatePhone, task.candidateEmail).filterNotNull().any { it.contains(value, ignoreCase = true) }
    }

    private fun validatePage(page: Int, pageSize: Int) {
        if (page < 1 || pageSize !in 1..100) throw BusinessException("INVALID_PAGE", "page 必须从1开始，pageSize范围为1到100", HttpStatus.BAD_REQUEST)
    }

    private companion object {
        val CONTENT_STATUSES = setOf(TaskStatus.SUBMITTED, TaskStatus.REVIEWING, TaskStatus.REVIEWED, TaskStatus.ARCHIVED)
        val RECORD_STATUSES = setOf(TaskStatus.REVIEWED, TaskStatus.ARCHIVED)
    }
}
