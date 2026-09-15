package com.acme.assessment.repository

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.entity.AssessmentAssignment
import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.AssessmentTemplate
import com.acme.assessment.entity.AssessmentTemplateVersion
import com.acme.assessment.entity.AssessmentAnswer
import com.acme.assessment.entity.AssessmentFile
import com.acme.assessment.entity.AssessmentReview
import com.acme.assessment.entity.JobPosition
import com.acme.assessment.entity.RecruitmentPosition
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.entity.Role
import com.acme.assessment.entity.User
import com.acme.assessment.entity.Department
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.time.Instant

interface RoleRepository : JpaRepository<Role, Long> {
    fun findByRoleCode(roleCode: String): Role?
}

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean
    fun findByPhone(phone: String): User?
    fun findByDingtalkUserId(dingtalkUserId: String): User?
    fun findAllByStatusOrderByCreatedAtAsc(status: UserStatus): List<User>
    fun findAllByIdIn(ids: Collection<Long>): List<User>
    fun findAllByRoleIdAndPositionIdAndStatus(roleId: Long, positionId: Long, status: UserStatus): List<User>
}

interface DepartmentRepository : JpaRepository<Department, Long> {
    fun existsByDepartmentCode(departmentCode: String): Boolean
    fun existsByDepartmentName(departmentName: String): Boolean
}

interface JobPositionRepository : JpaRepository<JobPosition, Long> {
    fun findAllByIdIn(ids: Collection<Long>): List<JobPosition>
}
interface RecruitmentPositionRepository : JpaRepository<RecruitmentPosition, Long> {
    fun findAllByIdIn(ids: Collection<Long>): List<RecruitmentPosition>
    fun existsByDepartmentNameAndPositionName(departmentName: String, positionName: String): Boolean
    fun existsByDepartmentNameAndPositionNameAndIdNot(departmentName: String, positionName: String, id: Long): Boolean
}
interface AssessmentTemplateRepository : JpaRepository<AssessmentTemplate, Long> {
    fun existsByPositionId(positionId: Long): Boolean
}
interface AssessmentTemplateVersionRepository : JpaRepository<AssessmentTemplateVersion, Long> {
    fun findAllByTemplateId(templateId: Long): List<AssessmentTemplateVersion>
}

interface AssessmentTaskRepository : JpaRepository<AssessmentTask, Long> {
    fun existsByPositionId(positionId: Long): Boolean
    fun existsByTaskNo(taskNo: String): Boolean
    fun existsByTokenHash(tokenHash: String): Boolean
    fun findByTokenHash(tokenHash: String): AssessmentTask?
    @Query("""
        select task from AssessmentTask task
        where task.status <> com.acme.assessment.entity.TaskStatus.ARCHIVED
          and (:status is null or task.status = :status)
          and (
              :keyword = ''
              or lower(task.taskNo) like concat('%', lower(:keyword), '%')
              or lower(task.candidateName) like concat('%', lower(:keyword), '%')
              or lower(task.candidatePhone) like concat('%', lower(:keyword), '%')
              or lower(task.candidateEmail) like concat('%', lower(:keyword), '%')
          )
        order by task.createdAt desc
    """)
    fun searchVisibleForManagers(
        @Param("status") status: TaskStatus?,
        @Param("keyword") keyword: String,
        pageable: Pageable,
    ): Page<AssessmentTask>
    @Query("""
        select task from AssessmentTask task
        where task.hrUserId = :hrUserId
          and task.status <> com.acme.assessment.entity.TaskStatus.ARCHIVED
          and (:status is null or task.status = :status)
          and (
              :keyword = ''
              or lower(task.taskNo) like concat('%', lower(:keyword), '%')
              or lower(task.candidateName) like concat('%', lower(:keyword), '%')
              or lower(task.candidatePhone) like concat('%', lower(:keyword), '%')
              or lower(task.candidateEmail) like concat('%', lower(:keyword), '%')
          )
        order by task.createdAt desc
    """)
    fun searchVisibleForHr(
        @Param("hrUserId") hrUserId: Long,
        @Param("status") status: TaskStatus?,
        @Param("keyword") keyword: String,
        pageable: Pageable,
    ): Page<AssessmentTask>
    @Query("select task.status as status, count(task) as total from AssessmentTask task where task.status <> com.acme.assessment.entity.TaskStatus.ARCHIVED group by task.status")
    fun countVisibleByStatusForManagers(): List<TaskStatusCount>
    @Query("select task.status as status, count(task) as total from AssessmentTask task where task.hrUserId = :hrUserId and task.status <> com.acme.assessment.entity.TaskStatus.ARCHIVED group by task.status")
    fun countVisibleByStatusForHr(@Param("hrUserId") hrUserId: Long): List<TaskStatusCount>
    @Query("""
        select task from AssessmentTask task
        where task.status = com.acme.assessment.entity.TaskStatus.ARCHIVED
          and (:hrUserId is null or task.hrUserId = :hrUserId)
          and (:positionId is null or task.positionId = :positionId)
          and (:keyword = '' or lower(task.taskNo) like concat('%', lower(:keyword), '%') or lower(task.candidateName) like concat('%', lower(:keyword), '%') or lower(task.candidatePhone) like concat('%', lower(:keyword), '%') or lower(task.candidateEmail) like concat('%', lower(:keyword), '%'))
          and (:conclusion is null or task.finalConclusion = :conclusion or exists (select review.id from AssessmentReview review join AssessmentAssignment assignment on review.assignmentId = assignment.id where assignment.taskId = task.id and review.conclusion = :conclusion))
        order by task.archivedAt desc
    """)
    fun searchRecords(
        @Param("hrUserId") hrUserId: Long?, @Param("positionId") positionId: Long?,
        @Param("keyword") keyword: String, @Param("conclusion") conclusion: ReviewConclusion?,
        pageable: Pageable,
    ): Page<AssessmentTask>
    fun existsByTemplateVersionId(templateVersionId: Long): Boolean
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AssessmentTask task where task.id = :taskId")
    fun findByIdForUpdate(@Param("taskId") taskId: Long): AssessmentTask?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AssessmentTask task where task.tokenHash = :tokenHash")
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): AssessmentTask?
    @Query(
        "select task.id from AssessmentTask task " +
            "where task.deadline <= :now and task.submittedAt is null and task.status in :statuses",
    )
    fun findOverdueUnsubmittedIds(
        @Param("now") now: Instant,
        @Param("statuses") statuses: Collection<TaskStatus>,
    ): List<Long>
}

interface TaskStatusCount {
    val status: TaskStatus
    val total: Long
}

interface AssessmentAssignmentRepository : JpaRepository<AssessmentAssignment, Long> {
    fun findAllByTaskId(taskId: Long): List<AssessmentAssignment>
    fun findAllByTaskIdIn(taskIds: Collection<Long>): List<AssessmentAssignment>
    fun findAllByReviewerUserId(reviewerUserId: Long): List<AssessmentAssignment>
}
interface AssessmentAnswerRepository : JpaRepository<AssessmentAnswer, Long> {
    fun findAllByTaskId(taskId: Long): List<AssessmentAnswer>
    fun findByTaskIdAndQuestionId(taskId: Long, questionId: String): AssessmentAnswer?
}

interface AssessmentFileRepository : JpaRepository<AssessmentFile, Long> {
    fun findAllByTaskId(taskId: Long): List<AssessmentFile>
}

interface AssessmentReviewRepository : JpaRepository<AssessmentReview, Long> {
    fun findByAssignmentId(assignmentId: Long): AssessmentReview?
    fun findAllByAssignmentIdIn(assignmentIds: Collection<Long>): List<AssessmentReview>
}
interface OperationLogRepository : JpaRepository<OperationLog, Long> {
    fun findAllByTaskIdOrderByCreatedAtAsc(taskId: Long): List<OperationLog>
}

interface NotificationRepository : JpaRepository<Notification, Long>
