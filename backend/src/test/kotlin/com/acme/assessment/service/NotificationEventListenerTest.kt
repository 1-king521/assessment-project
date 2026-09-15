package com.acme.assessment.service

import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.JobPosition
import com.acme.assessment.entity.RecruitmentPosition
import com.acme.assessment.entity.Notification
import com.acme.assessment.entity.User
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.RecruitmentPositionRepository
import com.acme.assessment.repository.NotificationRepository
import com.acme.assessment.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class NotificationEventListenerTest {
    private val taskRepository = mock<AssessmentTaskRepository>()
    private val assignmentRepository = mock<AssessmentAssignmentRepository>()
    private val userRepository = mock<UserRepository>()
    private val positionRepository = mock<RecruitmentPositionRepository>()
    private val notificationRepository = mock<NotificationRepository>()
    private val dingTalkUserService = mock<DingTalkUserService>()
    private val now = Instant.parse("2026-09-02T08:18:47.032731Z")
    private val listener = NotificationEventListener(
        taskRepository,
        assignmentRepository,
        userRepository,
        positionRepository,
        notificationRepository,
        dingTalkUserService,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @BeforeEach
    fun setUp() {
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `formats candidate submission time in Shanghai time`() {
        stubNotificationData(submittedAt = now, updatedAt = now)

        listener.onCandidateSubmitted(CandidateSubmittedEvent(taskId = 1, hrUserId = 2))

        assertThat(savedNotification().content).isEqualTo(
            "【候选人提交提醒】\n候选人：wang14\n应聘岗位：Java开发工程师\n" +
                "任务编号：TEST20260902081847\n提交时间：2026-09-02 16:18:47"
        )
    }

    @Test
    fun `uses the same time format for reviewer assignment`() {
        stubNotificationData(submittedAt = null, updatedAt = now)

        listener.onReviewersAssigned(ReviewersAssignedEvent(taskId = 1, reviewerUserIds = setOf(3)))

        assertThat(savedNotification().content).isEqualTo(
            "【评估任务提醒】\n候选人：wang14\n应聘岗位：Java开发工程师\n" +
                "任务编号：TEST20260902081847\n分配时间：2026-09-02 16:18:47"
        )
    }

    @Test
    fun `notifies hr when all reviews are completed`() {
        stubNotificationData(submittedAt = now, updatedAt = now)

        listener.onAllReviewsCompleted(AllReviewsCompletedEvent(taskId = 1, hrUserId = 2, reviewerCount = 2))

        val notification = savedNotification()
        assertThat(notification.title).isEqualTo("评估已全部完成")
        assertThat(notification.content).isEqualTo(
            "【评估完成提醒】\n候选人：wang14\n应聘岗位：Java开发工程师\n" +
                "任务编号：TEST20260902081847\n评估人数：2\n完成时间：2026-09-02 16:18:47"
        )
    }

    private fun stubNotificationData(submittedAt: Instant?, updatedAt: Instant) {
        val task = AssessmentTask(
            id = 1,
            taskNo = "TEST20260902081847",
            candidateName = "wang14",
            positionId = 4,
            submittedAt = submittedAt,
            updatedAt = updatedAt,
        )
        whenever(taskRepository.findById(1)).thenReturn(Optional.of(task))
        whenever(userRepository.findById(any())).thenReturn(Optional.of(User(id = 2)))
        whenever(positionRepository.findById(4)).thenReturn(
            Optional.of(RecruitmentPosition(id = 4, positionName = "Java开发工程师"))
        )
    }

    private fun savedNotification(): Notification {
        val captor = argumentCaptor<Notification>()
        verify(notificationRepository, times(2)).save(captor.capture())
        return captor.lastValue
    }
}



