package com.acme.assessment.publicapi

import com.acme.assessment.config.AppProperties
import com.acme.assessment.domain.AssessmentTask
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.task.SecureTokenService
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.NotFoundException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class SendCodeResult(val expiresAt: Instant, val debugCode: String?)
data class VerifyCodeResult(val cookie: ResponseCookie, val expiresAt: Instant)

@Service
class CandidateSessionService(
    private val taskRepository: AssessmentTaskRepository,
    private val tokenService: SecureTokenService,
    private val redis: StringRedisTemplate,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val random = SecureRandom()
    private val memory = ConcurrentHashMap<String, MemoryValue>()

    fun cookieName(): String = properties.candidate.cookieName

    fun sendCode(rawToken: String, phone: String): SendCodeResult {
        val task = findTask(rawToken)
        validateTask(task)
        val normalizedPhone = normalizePhone(phone)
        task.candidatePhone?.let {
            if (normalizePhone(it) != normalizedPhone) {
                throw BusinessException("PHONE_MISMATCH", "手机号与测评任务不一致")
            }
        }
        val code = (100000 + random.nextInt(900000)).toString()
        val key = codeKey(task, normalizedPhone)
        val expiresAt = clock.instant().plusSeconds(properties.candidate.codeTtlSeconds)
        put(key, code, properties.candidate.codeTtlSeconds)
        return SendCodeResult(expiresAt, if (properties.candidate.debugCodeResponse) code else null)
    }

    fun verifyCode(rawToken: String, phone: String, code: String): VerifyCodeResult {
        val task = findTask(rawToken)
        validateTask(task)
        val normalizedPhone = normalizePhone(phone)
        task.candidatePhone?.let {
            if (normalizePhone(it) != normalizedPhone) throw BusinessException("PHONE_MISMATCH", "手机号与测评任务不一致")
        }
        val key = codeKey(task, normalizedPhone)
        val expected = get(key) ?: throw BusinessException("CODE_EXPIRED", "验证码已过期或不存在")
        if (expected != code.trim()) throw BusinessException("CODE_INVALID", "验证码错误")
        remove(key)
        val now = clock.instant()
        if (task.candidatePhone == null) {
            task.candidatePhone = normalizedPhone
            task.phoneBoundAt = now
        }
        task.phoneVerifiedAt = now
        task.updatedAt = now
        taskRepository.save(task)
        val sessionId = tokenService.generate()
        put(sessionKey(sessionId), requireNotNull(task.id).toString(), properties.candidate.sessionTtlSeconds)
        val cookie = ResponseCookie.from(properties.candidate.cookieName, sessionId)
            .httpOnly(true)
            .secure(properties.candidate.cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofSeconds(properties.candidate.sessionTtlSeconds))
            .build()
        return VerifyCodeResult(cookie, now.plusSeconds(properties.candidate.sessionTtlSeconds))
    }

    fun requireSession(rawToken: String, sessionId: String?): AssessmentTask {
        val task = findTask(rawToken)
        validateTask(task)
        if (sessionId.isNullOrBlank()) throw BusinessException("CANDIDATE_SESSION_REQUIRED", "请先完成手机号验证", HttpStatus.UNAUTHORIZED)
        val storedTaskId = get(sessionKey(sessionId))
        if (storedTaskId != task.id.toString()) throw BusinessException("CANDIDATE_SESSION_INVALID", "候选人会话无效或已过期", HttpStatus.UNAUTHORIZED)
        if (task.phoneVerifiedAt == null) throw BusinessException("PHONE_NOT_VERIFIED", "请先完成手机号验证", HttpStatus.UNAUTHORIZED)
        return task
    }

    private fun findTask(rawToken: String): AssessmentTask {
        if (rawToken.isBlank() || rawToken.length < 20) throw invalidLink()
        return taskRepository.findByTokenHash(tokenService.hash(rawToken)) ?: throw invalidLink()
    }

    private fun validateTask(task: AssessmentTask) {
        val now = clock.instant()
        if (task.status.name in setOf("REVOKED", "ARCHIVED")) throw invalidLink()
        if (now.isAfter(task.deadline) && task.status.name !in setOf("SUBMITTED", "REVIEWING", "REVIEWED")) {
            throw BusinessException("TASK_EXPIRED", "测评链接已过期")
        }
        if (task.status.name == "DRAFT") throw ConflictException("TASK_NOT_SENT", "测评链接尚未发送")
    }

    private fun normalizePhone(phone: String): String = phone.trim().also {
        if (!it.matches(Regex("^\\+?[0-9]{6,30}$"))) throw BusinessException("INVALID_PHONE", "手机号格式不正确")
    }

    private fun codeKey(task: AssessmentTask, phone: String) = "candidate:code:${task.id}:$phone"
    private fun sessionKey(sessionId: String) = "candidate:session:$sessionId"

    private fun put(key: String, value: String, ttlSeconds: Long) {
        if (properties.candidate.storeMode.equals("REDIS", true)) {
            redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds))
        } else {
            memory[key] = MemoryValue(value, clock.instant().plusSeconds(ttlSeconds))
        }
    }

    private fun get(key: String): String? {
        if (properties.candidate.storeMode.equals("REDIS", true)) return redis.opsForValue().get(key)
        val value = memory[key] ?: return null
        if (clock.instant().isAfter(value.expiresAt)) {
            memory.remove(key)
            return null
        }
        return value.value
    }

    private fun remove(key: String) {
        if (properties.candidate.storeMode.equals("REDIS", true)) redis.delete(key) else memory.remove(key)
    }

    private fun invalidLink() = BusinessException("INVALID_ASSESSMENT_LINK", "测评链接无效或已失效", HttpStatus.NOT_FOUND)
    private data class MemoryValue(val value: String, val expiresAt: Instant)
}
