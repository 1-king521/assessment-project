package com.acme.assessment.service

import com.acme.assessment.entity.Notification
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.RecruitmentPositionRepository
import com.acme.assessment.repository.NotificationRepository
import com.acme.assessment.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.event.TransactionPhase
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class NotificationEventListener(
    private val taskRepository: AssessmentTaskRepository,
    private val assignmentRepository: AssessmentAssignmentRepository,
    private val userRepository: UserRepository,
    private val positionRepository: RecruitmentPositionRepository,
    private val notificationRepository: NotificationRepository,
    private val dingTalkUserService: DingTalkUserService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val shanghaiFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"))

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCandidateSubmitted(event: CandidateSubmittedEvent) {
        notifyUser(event.taskId, event.hrUserId, "CANDIDATE_SUBMITTED")
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCandidateAbandoned(event: CandidateAbandonedEvent) {
        notifyUser(event.taskId, event.hrUserId, "CANDIDATE_ABANDONED")
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onReviewersAssigned(event: ReviewersAssignedEvent) {
        val assignmentIds = assignmentRepository.findAllByTaskId(event.taskId)
            .filter { it.reviewerUserId in event.reviewerUserIds }
            .associate { it.reviewerUserId to it.id }
        event.reviewerUserIds.forEach {
            notifyUser(
                event.taskId,
                it,
                "REVIEWER_ASSIGNED",
                reviewDueAt = event.reviewDueAt,
                assignmentId = assignmentIds[it],
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onReviewOverdue(event: ReviewOverdueEvent) {
        notifyUser(event.taskId, event.reviewerUserId, "REVIEW_OVERDUE", reviewDueAt = event.reviewDueAt, assignmentId = event.assignmentId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun sendReviewOverdue(event: ReviewOverdueEvent): Boolean =
        notifyUser(event.taskId, event.reviewerUserId, "REVIEW_OVERDUE", reviewDueAt = event.reviewDueAt, assignmentId = event.assignmentId)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onAllReviewsCompleted(event: AllReviewsCompletedEvent) {
        notifyUser(event.taskId, event.hrUserId, "ALL_REVIEWS_COMPLETED", event.reviewerCount)
    }

    private fun notifyUser(
        taskId: Long,
        receiverUserId: Long,
        type: String,
        reviewerCount: Int? = null,
        reviewDueAt: Instant? = null,
        assignmentId: Long? = null,
    ): Boolean {
        val task = taskRepository.findById(taskId).orElse(null) ?: return false
        val receiver = userRepository.findById(receiverUserId).orElse(null) ?: return false
        val positionName = positionRepository.findById(task.positionId).orElse(null)?.positionName ?: "未知岗位"
        val title = when (type) {
            "CANDIDATE_SUBMITTED" -> "有新的候选人提交"
            "CANDIDATE_ABANDONED" -> "候选人主动放弃测评"
            "ALL_REVIEWS_COMPLETED" -> "评估已全部完成"
            "REVIEW_OVERDUE" -> "评估任务已超时"
            else -> "你被分配了评估任务"
        }
        val content = when (type) {
            "CANDIDATE_SUBMITTED" -> {
            "【候选人提交提醒】\n候选人：${task.candidateName}\n应聘岗位：$positionName\n任务编号：${task.taskNo}\n提交时间：${task.submittedAt?.let(::formatShanghai) ?: "--"}"
            }
            "CANDIDATE_ABANDONED" -> {
                "【候选人放弃提醒】\n候选人：${task.candidateName}\n应聘岗位：$positionName\n任务编号：${task.taskNo}\n" +
                    "放弃时间：${task.finalConclusionAt?.let(::formatShanghai) ?: "--"}\n放弃原因：${task.finalConclusionReason ?: "--"}"
            }
            "ALL_REVIEWS_COMPLETED" -> {
                "【评估完成提醒】\n候选人：${task.candidateName}\n应聘岗位：$positionName\n任务编号：${task.taskNo}\n评估人数：${reviewerCount ?: 0}\n完成时间：${formatShanghai(task.reviewedAt ?: clock.instant())}"
            }
            "REVIEW_OVERDUE" -> {
                "【评估任务超时提醒】\n候选人：${task.candidateName}\n应聘岗位：$positionName\n任务编号：${task.taskNo}\n评估截止时间：${reviewDueAt?.let(::formatShanghai) ?: "--"}\n该任务尚未完成评估，请尽快处理。"
            }
            else -> {
                "【评估任务提醒】\n候选人：${task.candidateName}\n应聘岗位：$positionName\n任务编号：${task.taskNo}\n分配时间：${formatShanghai(task.updatedAt)}" +
                    (reviewDueAt?.let { "\n评估截止时间：${formatShanghai(it)}" } ?: "")
            }
        }
        val record = notificationRepository.save(Notification(
            receiverUserId = receiverUserId, taskId = taskId, type = type,
            title = title, content = content, createdAt = clock.instant(),
        ))
        try {
            val reviewerNotification = type == "REVIEWER_ASSIGNED" || type == "REVIEW_OVERDUE"
            val actionPath = if (reviewerNotification) {
                assignmentId?.let { "/review?assignmentId=$it" } ?: "/review"
            } else {
                "/?taskId=$taskId"
            }
            val actionLabel = if (reviewerNotification) "查看评估任务" else "查看任务详情"
            dingTalkUserService.sendText(receiver, title, content, actionPath, actionLabel)
            record.sendStatus = "SENT"
            record.sentAt = clock.instant()
        } catch (ex: Exception) {
            logger.warn("DingTalk notification failed for notification {}", record.id, ex)
            record.sendStatus = if (receiver.dingtalkUserId.isNullOrBlank()) "WAITING_DINGTALK_USER" else "FAILED"
            record.sendError = ex.message?.take(500) ?: "钉钉通知发送失败"
        }
        notificationRepository.save(record)
        return record.sendStatus == "SENT"
    }

    private fun formatShanghai(value: Instant): String = shanghaiFormatter.format(value)
}
