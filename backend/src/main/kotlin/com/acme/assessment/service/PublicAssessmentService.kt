package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.entity.AssessmentAnswer
import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.repository.AssessmentAnswerRepository
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentFileRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.RecruitmentPositionRepository
import com.acme.assessment.repository.OperationLogRepository
import com.acme.assessment.service.SecureTokenService
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.NotFoundException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class PublicAssessmentService(
    private val taskRepository: AssessmentTaskRepository,
    private val answerRepository: AssessmentAnswerRepository,
    private val assignmentRepository: AssessmentAssignmentRepository,
    private val fileRepository: AssessmentFileRepository,
    private val templateVersionRepository: AssessmentTemplateVersionRepository,
    private val templateRepository: AssessmentTemplateRepository,
    private val positionRepository: RecruitmentPositionRepository,
    private val operationLogRepository: OperationLogRepository,
    private val tokenService: SecureTokenService,
    private val candidateAccess: CandidateAccessService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher = ApplicationEventPublisher { },
) {
    @Transactional
    fun open(rawToken: String): PublicAssessmentOverviewResponse {
        //校验token，看是否能找到测评任务
        val task = loadAccessibleTask(rawToken)
        val now = clock.instant()
        if (task.status == TaskStatus.ARCHIVED) {
            // Candidate-abandoned links remain readable so the result survives a refresh.
        } else if (task.openedAt == null) {
            val previousStatus = task.status
            task.openedAt = now
            task.status = TaskStatus.OPENED
            log(task, "LINK_ACCESSED", previousStatus.name, TaskStatus.OPENED.name, now)
        } else {
            task.lastAccessAt = now
        }
        task.lastAccessAt = now
        val position = positionRepository.findById(task.positionId).orElseThrow { NotFoundException("招聘岗位") }
        return PublicAssessmentOverviewResponse(
            taskNo = task.taskNo,
            candidateName = task.candidateName,
            positionId = task.positionId,
            positionName = position.positionName,
            deadline = task.deadline,
            status = task.status.name,
            abandonmentSource = task.abandonmentSource,
            abandonmentReason = task.finalConclusionReason,
            abandonedAt = task.finalConclusionAt,
        )
    }

    @Transactional(readOnly = true)
    fun getDraft(rawToken: String): PublicAssessmentResponse {
        val task = candidateAccess.requireAccess(rawToken)
        return toResponse(task)
    }

    @Transactional
    fun saveDraft(rawToken: String, request: SaveDraftRequest): SaveDraftResponse {
        val task = candidateAccess.requireAccess(rawToken)
        rejectAfterSubmit(task)
        validateAnswers(request.answers)
        val currentVersion = answerRepository.findAllByTaskId(task.id!!).maxOfOrNull { it.draftVersion } ?: 0
        if (request.draftVersion != null && request.draftVersion != currentVersion) {
            throw ConflictException("DRAFT_VERSION_CONFLICT", "草稿已被其他页面更新，请刷新后重试")
        }
        // 空草稿也可以推进任务状态，但没有答案记录可携带版本号，因此保持当前版本。
        val nextVersion = if (request.answers.isEmpty()) currentVersion else currentVersion + 1
        val now = clock.instant()
        request.answers.forEach { input ->
            val answer = answerRepository.findByTaskIdAndQuestionId(task.id!!, input.questionId)
                ?: AssessmentAnswer(taskId = task.id!!, questionId = input.questionId, createdAt = now)
            answer.answerJson = parseJson(input.answerJson).toString()
            answer.draftVersion = nextVersion
            answer.updatedAt = now
            answerRepository.save(answer)
        }
        if (task.status == TaskStatus.OPENED) task.status = TaskStatus.IN_PROGRESS
        task.draftSavedAt = now
        task.lastAccessAt = now
        task.updatedAt = now
        log(task, "DRAFT_SAVED", null, task.status.name, now)
        return SaveDraftResponse(task.taskNo, nextVersion, task.status.name, now)
    }

    @Transactional
    fun submit(rawToken: String, request: SubmitAssessmentRequest): SubmitAssessmentResponse {
        val task = candidateAccess.requireAccessForUpdate(rawToken)
        if (task.status == TaskStatus.SUBMITTED || task.status == TaskStatus.REVIEWING || task.status == TaskStatus.REVIEWED) {
            return SubmitAssessmentResponse(task.taskNo, task.status.name, task.submittedAt ?: clock.instant())
        }
        rejectAfterSubmit(task)
        if (request.confirm != "SUBMIT") throw BusinessException("SUBMIT_NOT_CONFIRMED", "请确认提交测评")
        val version = templateVersionRepository.findById(task.templateVersionId)
            .orElseThrow { NotFoundException("模板版本") }
        val schema = parseJson(version.schemaJson)
        validateRequiredAnswers(task, schema)
        if (fileRepository.findAllByTaskId(requireNotNull(task.id)).any { it.uploadStatus.name == "UPLOADING" }) {
            throw ConflictException("FILE_UPLOAD_INCOMPLETE", "存在尚未上传完成的文件")
        }
        validateRequiredFiles(task, schema)
        val now = clock.instant()
        answerRepository.findAllByTaskId(requireNotNull(task.id)).forEach { it.submittedAt = now }
        assignmentRepository.findAllByTaskId(requireNotNull(task.id))
            .filter { it.status == AssignmentStatus.WAITING_CANDIDATE }
            .forEach(assignmentRepository::delete)
        task.status = TaskStatus.SUBMITTED
        task.submittedAt = now
        task.lastAccessAt = now
        task.updatedAt = now
        log(task, "TASK_SUBMITTED", null, TaskStatus.SUBMITTED.name, now, request.idempotencyKey)
        eventPublisher.publishEvent(CandidateSubmittedEvent(requireNotNull(task.id), task.hrUserId))
        return SubmitAssessmentResponse(task.taskNo, task.status.name, now)
    }

    @Transactional
    fun abandon(rawToken: String, request: AbandonAssessmentRequest): AbandonAssessmentResponse {
        val reason = request.reason.trim()
        if (reason.length !in 5..500) {
            throw BusinessException("INVALID_ABANDONMENT_REASON", "放弃原因须为5至500个字")
        }
        if (rawToken.isBlank() || rawToken.length < 20) throw invalidLink()
        val task = taskRepository.findByTokenHashForUpdate(tokenService.hash(rawToken)) ?: throw invalidLink()
        if (task.status == TaskStatus.ARCHIVED && task.abandonmentSource == AbandonmentSource.CANDIDATE) {
            return abandonmentResponse(task)
        }
        val now = clock.instant()
        if (!task.deadline.isAfter(now)) {
            throw BusinessException("TASK_EXPIRED", "测评链接已过期")
        }
        if (task.status !in ABANDONABLE_STATUSES) {
            throw ConflictException("TASK_CANNOT_BE_ABANDONED", "当前状态不能放弃测评")
        }

        val previousStatus = task.status
        assignmentRepository.findAllByTaskId(requireNotNull(task.id))
            .filter { it.status == AssignmentStatus.WAITING_CANDIDATE }
            .forEach {
                it.status = AssignmentStatus.CANCELLED
                it.cancelledAt = now
                it.updatedAt = now
            }
        task.status = TaskStatus.ARCHIVED
        task.archivedAt = now
        task.finalConclusion = ReviewConclusion.ABANDONED
        task.finalConclusionAt = now
        task.finalConclusionReason = reason
        task.abandonmentSource = AbandonmentSource.CANDIDATE
        task.lastAccessAt = now
        task.updatedAt = now
        operationLogRepository.save(OperationLog(
            taskId = task.id,
            operatorType = "CANDIDATE",
            action = "TASK_ABANDONED",
            fromStatus = previousStatus.name,
            toStatus = TaskStatus.ARCHIVED.name,
            detailJson = objectMapper.writeValueAsString(mapOf(
                "idempotencyKey" to request.idempotencyKey,
                "source" to AbandonmentSource.CANDIDATE.name,
                "reason" to reason,
            )),
            createdAt = now,
        ))
        eventPublisher.publishEvent(CandidateAbandonedEvent(requireNotNull(task.id), task.hrUserId))
        return abandonmentResponse(task)
    }

    private fun loadAccessibleTask(rawToken: String): AssessmentTask {
        if (rawToken.isBlank() || rawToken.length < 20) throw invalidLink()
        val task = taskRepository.findByTokenHash(tokenService.hash(rawToken)) ?: throw invalidLink()
        val now = clock.instant()
        if (task.status == TaskStatus.REVOKED) throw invalidLink()
        if (task.status == TaskStatus.ARCHIVED) {
            if (task.abandonmentSource == AbandonmentSource.CANDIDATE) return task
            throw invalidLink()
        }
        if (now.isAfter(task.deadline) && task.status !in setOf(TaskStatus.SUBMITTED, TaskStatus.REVIEWING, TaskStatus.REVIEWED)) {
            if (task.status != TaskStatus.EXPIRED) {
                task.status = TaskStatus.EXPIRED
                task.updatedAt = now
            }
            throw BusinessException("TASK_EXPIRED", "测评链接已过期")
        }
        return task
    }

    private fun toResponse(task: AssessmentTask): PublicAssessmentResponse {
        val version = templateVersionRepository.findById(task.templateVersionId)
            .orElseThrow { NotFoundException("模板版本") }
        val template = templateRepository.findById(version.templateId).orElseThrow { NotFoundException("测评模板") }
        val position = positionRepository.findById(task.positionId).orElseThrow { NotFoundException("招聘岗位") }
        return PublicAssessmentResponse(
            taskNo = task.taskNo,
            candidateName = task.candidateName,
            positionId = task.positionId,
            positionName = position.positionName,
            deadline = task.deadline,
            status = task.status.name,
            templateVersionId = version.id!!,
            schemaJson = version.schemaJson,
            answers = answerRepository.findAllByTaskId(task.id!!).map {
                AnswerResponse(it.questionId, it.answerJson, it.draftVersion)
            },
            files = fileRepository.findAllByTaskId(task.id!!).filter { it.uploadStatus.name != "DELETED" }.map {
                FileResponse(requireNotNull(it.id), it.questionId, it.fileName, it.sizeBytes, it.uploadStatus.name)
            },
        )
    }

    private fun validateAnswers(answers: List<DraftAnswerRequest>) {
        if (answers.map { it.questionId }.toSet().size != answers.size) {
            throw BusinessException("DUPLICATE_QUESTION_ANSWER", "同一道题只能提交一个答案")
        }
        answers.forEach { parseJson(it.answerJson) }
    }

    private fun validateRequiredAnswers(task: AssessmentTask, schema: JsonNode) {
        val answers = answerRepository.findAllByTaskId(requireNotNull(task.id)).associateBy { it.questionId }
        val questions = schema.path("questions")
        if (!questions.isArray) throw BusinessException("INVALID_TEMPLATE_SCHEMA", "模板缺少 questions 数组")
        val fileTypes = setOf("FILE", "FILES", "IMAGE", "ATTACHMENT")
        val missing = questions.filter {
            it.path("required").asBoolean(false) && it.path("type").asText("").uppercase() !in fileTypes
        }
            .mapNotNull { question ->
                val id = question.path("id").asText("")
                val answer = answers[id]?.answerJson?.let { parseJson(it) }
                if (id.isBlank() || answer == null || answer.isNull || (answer.isTextual && answer.asText().isBlank())) id else null
            }
        if (missing.isNotEmpty()) throw BusinessException("REQUIRED_ANSWER_MISSING", "存在未完成的必填题：${missing.joinToString(",")}")
    }

    private fun validateRequiredFiles(task: AssessmentTask, schema: JsonNode) {
        val completed = fileRepository.findAllByTaskId(requireNotNull(task.id))
            .filter { it.uploadStatus.name == "COMPLETED" }
            .groupBy { it.questionId }
        val missing = schema.path("questions").filter { question ->
            question.path("required").asBoolean(false) &&
                question.path("type").asText("").uppercase() in setOf("FILE", "FILES", "IMAGE", "ATTACHMENT") &&
                completed[question.path("id").asText("")].isNullOrEmpty()
        }.map { it.path("id").asText("") }
        if (missing.isNotEmpty()) throw BusinessException("REQUIRED_FILE_MISSING", "存在未上传的必传文件：${missing.joinToString(",")}")
    }

    private fun rejectAfterSubmit(task: AssessmentTask) {
        if (task.status == TaskStatus.SUBMITTED || task.status == TaskStatus.REVIEWING || task.status == TaskStatus.REVIEWED) {
            throw ConflictException("TASK_ALREADY_SUBMITTED", "测评已经提交，不能继续修改")
        }
    }

    private fun parseJson(value: String): JsonNode = try {
        objectMapper.readTree(value) ?: throw IllegalArgumentException()
    } catch (_: Exception) {
        throw BusinessException("INVALID_ANSWER_JSON", "答案必须是合法 JSON")
    }

    private fun log(task: AssessmentTask, action: String, from: String?, to: String?, now: java.time.Instant, detail: String? = null) {
        operationLogRepository.save(OperationLog(
            taskId = task.id,
            operatorType = "CANDIDATE",
            action = action,
            fromStatus = from,
            toStatus = to,
            detailJson = detail?.let { objectMapper.writeValueAsString(mapOf("idempotencyKey" to it)) },
            createdAt = now,
        ))
    }

    private fun invalidLink() = BusinessException("INVALID_ASSESSMENT_LINK", "测评链接无效或已失效", HttpStatus.NOT_FOUND)

    private fun abandonmentResponse(task: AssessmentTask) = AbandonAssessmentResponse(
        taskNo = task.taskNo,
        status = task.status.name,
        conclusion = ReviewConclusion.ABANDONED,
        abandonmentSource = AbandonmentSource.CANDIDATE,
        reason = requireNotNull(task.finalConclusionReason),
        abandonedAt = requireNotNull(task.finalConclusionAt),
    )

    private companion object {
        val ABANDONABLE_STATUSES = setOf(TaskStatus.SENT, TaskStatus.OPENED, TaskStatus.IN_PROGRESS)
    }
}
