package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.service.AuthenticationService
import com.acme.assessment.config.AppProperties
import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.entity.RecordStatus
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.entity.TemplateVersionStatus
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.RecruitmentPositionRepository
import com.acme.assessment.repository.OperationLogRepository
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder
import java.time.Clock

@Service
class AssessmentTaskService(
    private val authenticationService: AuthenticationService,
    private val taskRepository: AssessmentTaskRepository,
    private val positionRepository: RecruitmentPositionRepository,
    private val templateRepository: AssessmentTemplateRepository,
    private val templateVersionRepository: AssessmentTemplateVersionRepository,
    private val operationLogRepository: OperationLogRepository,
    private val tokenService: SecureTokenService,
    private val taskNumberGenerator: TaskNumberGenerator,
    private val objectMapper: ObjectMapper,
    private val properties: AppProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun create(request: CreateAssessmentTaskRequest): CreateAssessmentTaskResponse {
        val actor = authenticationService.currentUser()
        if (!actor.isAnyRole("HR", "HR_MANAGER", "ADMIN")) {
            throw BusinessException("FORBIDDEN", "当前用户无权创建测评任务", HttpStatus.FORBIDDEN)
        }
        val now = clock.instant()
        if (!request.deadline.isAfter(now)) {
            throw BusinessException("INVALID_DEADLINE", "截止时间必须晚于当前时间")
        }
        val candidatePhone = request.candidatePhone.trim()
        if (!candidatePhone.matches(Regex("^\\+?[0-9]{6,30}$"))) {
            throw BusinessException("INVALID_PHONE", "手机号格式不正确")
        }

        val position = positionRepository.findById(request.positionId).orElseThrow { NotFoundException("招聘岗位") }
        if (position.status != RecordStatus.ACTIVE) {
            throw ConflictException("POSITION_INACTIVE", "招聘岗位未启用")
        }
        val version = templateVersionRepository.findById(request.templateVersionId)
            .orElseThrow { NotFoundException("测评模板版本") }
        if (version.versionStatus != TemplateVersionStatus.PUBLISHED) {
            throw ConflictException("TEMPLATE_VERSION_NOT_PUBLISHED", "只能使用已发布的模板版本")
        }
        val template = templateRepository.findById(version.templateId).orElseThrow { NotFoundException("测评模板") }
        if (template.positionId != position.id) {
            throw BusinessException("TEMPLATE_POSITION_MISMATCH", "模板版本不属于所选岗位")
        }

        val taskNo = generateUniqueTaskNo()
        val rawToken = generateUniqueToken()
        val task = taskRepository.save(
            AssessmentTask(
                taskNo = taskNo,
                candidateName = request.candidateName.trim(),
                candidatePhone = candidatePhone,
                candidateEmail = request.candidateEmail.clean(),
                candidateSource = request.candidateSource.clean(),
                positionId = requireNotNull(position.id),
                hrUserId = actor.id,
                templateVersionId = requireNotNull(version.id),
                tokenHash = tokenService.hash(rawToken),
                deadline = request.deadline,
                status = TaskStatus.DRAFT,
                createdBy = actor.id,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val taskId = requireNotNull(task.id)
        //日志记录
        operationLogRepository.save(
            OperationLog(
                taskId = taskId,
                operatorId = actor.id,
                action = "TASK_CREATED",
                toStatus = TaskStatus.DRAFT.name,
                detailJson = objectMapper.writeValueAsString(
                    mapOf("templateVersionId" to version.id),
                ),
                createdAt = now,
            ),
        )
        //生成带token的测评链接
        val url = UriComponentsBuilder.fromUriString(properties.publicBaseUrl)
            .pathSegment(rawToken)
            .build()
            .toUriString()
        return CreateAssessmentTaskResponse(
            id = taskId,
            taskNo = taskNo,
            status = task.status,
            assessmentUrl = url,
            deadline = task.deadline,
        )
    }

    @Transactional
    fun send(taskId: Long): SendAssessmentTaskResponse {
        val actor = authenticationService.currentUser()
        val task = taskRepository.findById(taskId).orElseThrow { NotFoundException("测评任务") }
        if (!actor.isAnyRole("HR_MANAGER", "ADMIN") && task.hrUserId != actor.id) {
            throw BusinessException("FORBIDDEN", "只能发送本人负责的测评任务", HttpStatus.FORBIDDEN)
        }
        if (task.status != TaskStatus.DRAFT) {
            throw ConflictException("INVALID_TASK_STATUS", "只有草稿状态的任务可以发送")
        }
        val now = clock.instant()
        if (!task.deadline.isAfter(now)) {
            throw ConflictException("TASK_EXPIRED", "任务截止时间已过，无法发送")
        }
        task.status = TaskStatus.SENT
        task.sentAt = now
        task.updatedAt = now
        operationLogRepository.save(
            OperationLog(
                taskId = taskId,
                operatorId = actor.id,
                action = "TASK_SENT",
                fromStatus = TaskStatus.DRAFT.name,
                toStatus = TaskStatus.SENT.name,
                createdAt = now,
            ),
        )
        return SendAssessmentTaskResponse(taskId, task.taskNo, task.status, now)
    }

    @Transactional
    fun regenerateLink(taskId: Long): RegenerateAssessmentLinkResponse {
        val actor = authenticationService.currentUser()
        val task = taskRepository.findById(taskId).orElseThrow { NotFoundException("测评任务") }
        if (!actor.isAnyRole("HR_MANAGER", "ADMIN") && task.hrUserId != actor.id) {
            throw BusinessException("FORBIDDEN", "只能操作本人负责的测评任务", HttpStatus.FORBIDDEN)
        }
        if (task.status !in setOf(TaskStatus.DRAFT, TaskStatus.SENT, TaskStatus.OPENED, TaskStatus.IN_PROGRESS)) {
            throw ConflictException("INVALID_TASK_STATUS", "当前状态不允许重新生成测试链接")
        }
        val rawToken = generateUniqueToken()
        val now = clock.instant()
        task.tokenHash = tokenService.hash(rawToken)
        task.updatedAt = now
        taskRepository.save(task)
        operationLogRepository.save(
            OperationLog(
                taskId = taskId,
                operatorId = actor.id,
                action = "ASSESSMENT_LINK_REGENERATED",
                detailJson = objectMapper.writeValueAsString(mapOf("reason" to "manual_regeneration")),
                createdAt = now,
            ),
        )
        val url = UriComponentsBuilder.fromUriString(properties.publicBaseUrl)
            .pathSegment(rawToken)
            .build()
            .toUriString()
        return RegenerateAssessmentLinkResponse(taskId, task.taskNo, task.status, url, now)
    }

    private fun generateUniqueTaskNo(): String = repeatUntilUnique(
        generator = taskNumberGenerator::next,
        exists = taskRepository::existsByTaskNo,
        errorCode = "TASK_NUMBER_GENERATION_FAILED",
    )

    private fun generateUniqueToken(): String = repeatUntilUnique(
        generator = tokenService::generate,
        exists = { taskRepository.existsByTokenHash(tokenService.hash(it)) },
        errorCode = "TOKEN_GENERATION_FAILED",
    )

    private fun repeatUntilUnique(
        generator: () -> String,
        exists: (String) -> Boolean,
        errorCode: String,
    ): String {
        repeat(5) {
            val candidate = generator()
            if (!exists(candidate)) return candidate
        }
        throw ConflictException(errorCode, "生成唯一标识失败，请重试")
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
