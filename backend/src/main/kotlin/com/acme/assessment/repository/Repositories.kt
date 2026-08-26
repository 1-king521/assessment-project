package com.acme.assessment.repository

import com.acme.assessment.domain.AssessmentAssignment
import com.acme.assessment.domain.AssessmentTask
import com.acme.assessment.domain.AssessmentTemplate
import com.acme.assessment.domain.AssessmentTemplateVersion
import com.acme.assessment.domain.AssessmentAnswer
import com.acme.assessment.domain.AssessmentFile
import com.acme.assessment.domain.AssessmentReview
import com.acme.assessment.domain.JobPosition
import com.acme.assessment.domain.OperationLog
import com.acme.assessment.domain.Role
import com.acme.assessment.domain.User
import com.acme.assessment.domain.Department
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository : JpaRepository<Role, Long> {
    fun findByRoleCode(roleCode: String): Role?
}

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean
    fun findAllByIdIn(ids: Collection<Long>): List<User>
}

interface DepartmentRepository : JpaRepository<Department, Long> {
    fun existsByDepartmentCode(departmentCode: String): Boolean
    fun existsByDepartmentName(departmentName: String): Boolean
}

interface JobPositionRepository : JpaRepository<JobPosition, Long>
interface AssessmentTemplateRepository : JpaRepository<AssessmentTemplate, Long>
interface AssessmentTemplateVersionRepository : JpaRepository<AssessmentTemplateVersion, Long>

interface AssessmentTaskRepository : JpaRepository<AssessmentTask, Long> {
    fun existsByTaskNo(taskNo: String): Boolean
    fun existsByTokenHash(tokenHash: String): Boolean
    fun findByTokenHash(tokenHash: String): AssessmentTask?
}

interface AssessmentAssignmentRepository : JpaRepository<AssessmentAssignment, Long> {
    fun findAllByTaskId(taskId: Long): List<AssessmentAssignment>
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
}
interface OperationLogRepository : JpaRepository<OperationLog, Long>
