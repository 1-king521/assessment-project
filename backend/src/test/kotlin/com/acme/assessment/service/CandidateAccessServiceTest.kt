package com.acme.assessment.service

import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.web.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class CandidateAccessServiceTest {
    private val taskRepository = mock<AssessmentTaskRepository>()
    private val tokenService = mock<SecureTokenService>()
    private val now = Instant.parse("2026-08-27T02:00:00Z")
    private val rawLinkToken = "candidate-link-token-long-enough"
    private val task = AssessmentTask(
        id = 42,
        taskNo = "TEST-42",
        candidatePhone = "13800000000",
        tokenHash = "link-hash",
        deadline = now.plus(Duration.ofDays(3)),
        status = TaskStatus.SENT,
    )
    private lateinit var service: CandidateAccessService

    @BeforeEach
    fun setUp() {
        service = CandidateAccessService(
            taskRepository,
            tokenService,
            Clock.fixed(now, ZoneOffset.UTC),
        )
        whenever(tokenService.hash(rawLinkToken)).thenReturn("link-hash")
        whenever(taskRepository.findByTokenHash("link-hash")).thenReturn(task)
    }

    @Test
    fun `valid link token grants access`() {
        assertThat(service.requireAccess(rawLinkToken)).isSameAs(task)
    }

    @Test
    fun `legacy draft link token grants access`() {
        task.status = TaskStatus.DRAFT

        assertThat(service.requireAccess(rawLinkToken)).isSameAs(task)
    }

    @Test
    fun `deadline instant is no longer submit eligible`() {
        task.deadline = now
        whenever(taskRepository.findByTokenHashForUpdate("link-hash")).thenReturn(task)

        assertThatThrownBy { service.requireAccessForUpdate(rawLinkToken) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("测评链接已过期")
    }
}
