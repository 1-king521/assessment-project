package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.service.AuthenticationService
import com.acme.assessment.entity.AssessmentAssignment
import com.acme.assessment.entity.AssessmentReview
import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.entity.ReviewConclusion
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.repository.AssessmentAnswerRepository
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentFileRepository
import com.acme.assessment.repository.AssessmentReviewRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.RecruitmentPositionRepository
import com.acme.assessment.repository.OperationLogRepository
import com.acme.assessment.service.FileStorageService
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock

@Service
class ReviewService(
    private val authenticationService: AuthenticationService,
    private val assignmentRepository: AssessmentAssignmentRepository,
    private val taskRepository: AssessmentTaskRepository,
    private val templateVersionRepository: AssessmentTemplateVersionRepository,
    private val reviewRepository: AssessmentReviewRepository,
    private val answerRepository: AssessmentAnswerRepository,
    private val fileRepository: AssessmentFileRepository,
    private val positionRepository: RecruitmentPositionRepository,
    private val operationLogRepository: OperationLogRepository,
    private val fileStorageService: FileStorageService,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher = ApplicationEventPublisher { },
) {
    @Transactional(readOnly = true)
    fun listMine(): List<ReviewAssignmentResponse> {
        val reviewer = requireReviewer()
        val assignments = assignmentRepository.findAllByReviewerUserId(reviewer.id)
            .filter { it.status in setOf(AssignmentStatus.PENDING, AssignmentStatus.IN_PROGRESS, AssignmentStatus.COMPLETED) }
        val tasks = taskRepository.findAllById(assignments.map { it.taskId }).associateBy { it.id }
        val positions = positionRepository.findAllByIdIn(tasks.values.map { it.positionId }).associateBy { it.id }
        val reviews = reviewRepository.findAllByAssignmentIdIn(assignments.mapNotNull { it.id }).associateBy { it.assignmentId }
        return assignments.mapNotNull { assignment ->
            val task = tasks[assignment.taskId] ?: return@mapNotNull null
            val position = positions[task.positionId]
            ReviewAssignmentResponse(
                assignmentId = requireNotNull(assignment.id),
                taskId = requireNotNull(task.id),
                taskNo = task.taskNo,
                candidateName = task.candidateName,
                positionName = position?.positionName ?: "未知岗位",
                status = assignment.status,
                reviewDueAt = assignment.reviewDueAt,
                submittedAt = task.submittedAt,
                completedAt = assignment.completedAt,
                conclusion = reviews[requireNotNull(assignment.id)]?.conclusion,
            )
        }
    }

    @Transactional(readOnly = true)
    fun detail(assignmentId: Long): ReviewDetailResponse {
        val assignment = loadMine(assignmentId)
        val task = taskRepository.findById(assignment.taskId).orElseThrow { NotFoundException("测评任务") }
        val position = positionRepository.findById(task.positionId).orElseThrow { NotFoundException("招聘岗位") }
        val templateVersion = templateVersionRepository.findById(task.templateVersionId)
            .orElseThrow { NotFoundException("模板版本") }
        val review = reviewRepository.findByAssignmentId(assignmentId)
        return ReviewDetailResponse(
            assignmentId = assignmentId,
            taskId = requireNotNull(task.id),
            taskNo = task.taskNo,
            candidateName = task.candidateName,
            candidateEmail = task.candidateEmail,
            positionName = position.positionName,
            taskStatus = task.status.name,
            assignmentStatus = assignment.status,
            reviewDueAt = assignment.reviewDueAt,
            schemaJson = templateVersion.schemaJson,
            answers = answerRepository.findAllByTaskId(requireNotNull(task.id)).map {
                ReviewAnswerResponse(it.questionId, it.answerJson, it.submittedAt)
            },
            files = fileRepository.findAllByTaskId(requireNotNull(task.id)).filter { it.uploadStatus.name != "DELETED" }.map {
                ReviewFileResponse(requireNotNull(it.id), it.questionId, it.fileName, it.contentType, it.sizeBytes, it.uploadStatus.name)
            },
            review = review?.let { ReviewResultResponse(it.conclusion, it.reason, it.submittedAt) },
        )
    }

    @Transactional(readOnly = true)
    fun fileContent(assignmentId: Long, fileId: Long): ReviewFileContent {
        val assignment = loadMine(assignmentId)
        val file = fileStorageService.findForTask(fileId, assignment.taskId)
        if (file.uploadStatus.name != "COMPLETED") throw NotFoundException("测评文件内容")
        return ReviewFileContent(file.fileName, file.contentType, fileStorageService.loadContent(file))
    }

    @Transactional
    fun start(assignmentId: Long): ReviewDetailResponse {
        requireReviewer()
        val initial = loadMine(assignmentId)
        val task = taskRepository.findByIdForUpdate(initial.taskId) ?: throw NotFoundException("测评任务")
        val assignment = loadMine(assignmentId)
        if (assignment.status != AssignmentStatus.PENDING) {
            throw ConflictException("INVALID_ASSIGNMENT_STATUS", "只有待评估分配可以开始评估")
        }
        if (task.status != TaskStatus.SUBMITTED && task.status != TaskStatus.REVIEWING) {
            throw ConflictException("TASK_NOT_SUBMITTED", "候选人尚未提交测评")
        }
        val now = clock.instant()
        assignment.status = AssignmentStatus.IN_PROGRESS
        assignment.startedAt = now
        assignment.updatedAt = now
        if (task.status == TaskStatus.SUBMITTED) {
            task.status = TaskStatus.REVIEWING
            task.reviewStartedAt = now
            task.updatedAt = now
        }
        log(task, "REVIEW_STARTED", TaskStatus.SUBMITTED.name, TaskStatus.REVIEWING.name, now, assignmentId)
        return detail(assignmentId)
    }

    @Transactional
    fun submit(assignmentId: Long, request: SubmitReviewRequest): SubmitReviewResponse {
        val reviewer = requireReviewer()
        if (request.conclusion == ReviewConclusion.ABANDONED) {
            throw BusinessException("INVALID_REVIEW_CONCLUSION", "放弃测试只能由系统在候选人超时未提交时生成")
        }
        val assignment = loadMine(assignmentId)
        val task = taskRepository.findByIdForUpdate(assignment.taskId) ?: throw NotFoundException("测评任务")
        if (reviewRepository.findByAssignmentId(assignmentId) != null) {
            throw ConflictException("REVIEW_ALREADY_SUBMITTED", "评估结果已经提交")
        }
        if (assignment.status != AssignmentStatus.IN_PROGRESS) {
            throw ConflictException("INVALID_ASSIGNMENT_STATUS", "请先开始评估")
        }
        if (request.conclusion == ReviewConclusion.REJECTED && request.reason?.trim().isNullOrEmpty()) {
            throw BusinessException("REJECTION_REASON_REQUIRED", "不通过时必须填写原因")
        }
        val now = clock.instant()
        reviewRepository.save(AssessmentReview(
            assignmentId = assignmentId,
            conclusion = request.conclusion,
            reason = request.reason?.trim()?.takeIf { it.isNotEmpty() },
            score = null,
            submittedBy = reviewer.id,
            submittedAt = now,
            createdAt = now,
            updatedAt = now,
        ))
        assignment.status = AssignmentStatus.COMPLETED
        assignment.completedAt = now
        assignment.updatedAt = now
        val activeAssignments = assignmentRepository.findAllByTaskId(requireNotNull(task.id))
            .filter { it.status != AssignmentStatus.CANCELLED }
        val allCompleted = activeAssignments.isNotEmpty() && activeAssignments.all { it.status == AssignmentStatus.COMPLETED }
        if (allCompleted && task.status != TaskStatus.REVIEWED) {
            task.status = TaskStatus.REVIEWED
            task.reviewedAt = now
            eventPublisher.publishEvent(AllReviewsCompletedEvent(requireNotNull(task.id), task.hrUserId, activeAssignments.size))
        } else if (!allCompleted) {
            task.status = TaskStatus.REVIEWING
        }
        task.updatedAt = now
        log(task, "REVIEW_SUBMITTED", TaskStatus.REVIEWING.name, task.status.name, now, assignmentId)
        return SubmitReviewResponse(assignmentId, assignment.status, task.status.name, request.conclusion, now)
    }

    private fun requireReviewer() = authenticationService.currentUser().also {
        if (it.role != "REVIEWER") throw BusinessException("FORBIDDEN", "只有评估人员可以执行该操作", HttpStatus.FORBIDDEN)
    }

    private fun loadMine(assignmentId: Long): AssessmentAssignment {
        val reviewer = requireReviewer()
        val assignment = assignmentRepository.findById(assignmentId).orElseThrow { NotFoundException("评估分配") }
        if (assignment.reviewerUserId != reviewer.id) throw BusinessException("FORBIDDEN", "无权访问该评估任务", HttpStatus.FORBIDDEN)
        return assignment
    }

    private fun log(task: AssessmentTask, action: String, from: String?, to: String?, now: java.time.Instant, assignmentId: Long) {
        operationLogRepository.save(OperationLog(
            taskId = task.id,
            operatorId = authenticationService.currentUser().id,
            action = action,
            fromStatus = from,
            toStatus = to,
            detailJson = "{\"assignmentId\":$assignmentId}",
            createdAt = now,
        ))
    }
}
