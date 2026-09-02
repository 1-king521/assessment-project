package com.acme.assessment.service

import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class CandidateAccessService(
    private val taskRepository: AssessmentTaskRepository,
    private val tokenService: SecureTokenService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun requireAccess(rawToken: String): AssessmentTask {
        val task = findTask(rawToken)
        validateTask(task)
        return task
    }

    @Transactional
    fun requireAccessForUpdate(rawToken: String): AssessmentTask {
        if (rawToken.isBlank() || rawToken.length < 20) throw invalidLink()
        val task = taskRepository.findByTokenHashForUpdate(tokenService.hash(rawToken)) ?: throw invalidLink()
        validateTask(task)
        return task
    }

    private fun findTask(rawToken: String): AssessmentTask {
        if (rawToken.isBlank() || rawToken.length < 20) throw invalidLink()
        return taskRepository.findByTokenHash(tokenService.hash(rawToken)) ?: throw invalidLink()
    }

    private fun validateTask(task: AssessmentTask) {
        val now = clock.instant()
        if (task.status in setOf(TaskStatus.REVOKED, TaskStatus.ARCHIVED)) throw invalidLink()
        if (!task.deadline.isAfter(now) && task.status !in setOf(TaskStatus.SUBMITTED, TaskStatus.REVIEWING, TaskStatus.REVIEWED)) {
            throw BusinessException("TASK_EXPIRED", "测评链接已过期")
        }
        if (task.status == TaskStatus.DRAFT) throw ConflictException("TASK_NOT_SENT", "测评链接尚未发送")
        if (task.status in setOf(TaskStatus.SUBMITTED, TaskStatus.REVIEWING, TaskStatus.REVIEWED)) {
            throw ConflictException("TASK_ALREADY_SUBMITTED", "测评已经提交")
        }
    }

    private fun invalidLink() = BusinessException("INVALID_ASSESSMENT_LINK", "测评链接无效或已失效", HttpStatus.NOT_FOUND)
}
