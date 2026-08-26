package com.acme.assessment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

enum class RecordStatus { ACTIVE, INACTIVE }
enum class UserStatus { ACTIVE, LOCKED, DISABLED }
enum class TemplateStatus { DRAFT, ACTIVE, DISABLED }
enum class TemplateVersionStatus { DRAFT, PENDING, PUBLISHED, ARCHIVED }
enum class TaskStatus { DRAFT, SENT, OPENED, IN_PROGRESS, SUBMITTED, EXPIRED, REVOKED, REVIEWING, REVIEWED, ARCHIVED }
enum class AssignmentStatus { WAITING_CANDIDATE, PENDING, IN_PROGRESS, COMPLETED, CANCELLED }
enum class ReviewConclusion { PASS, REJECTED, RESERVED }

@Entity
@Table(name = "sys_role")
class Role(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "role_code", nullable = false, unique = true)
    var roleCode: String = "",
    @Column(name = "role_name", nullable = false)
    var roleName: String = "",
    var description: String? = null,
    var status: String = "ENABLED",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "sys_department")
class Department(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "department_code", nullable = false, unique = true)
    var departmentCode: String = "",
    @Column(name = "department_name", nullable = false, unique = true)
    var departmentName: String = "",
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    var status: RecordStatus = RecordStatus.ACTIVE,
    @Column(name = "created_by")
    var createdBy: Long? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_by")
    var updatedBy: Long? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "sys_user")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true)
    var username: String = "",
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",
    @Column(name = "real_name", nullable = false)
    var realName: String = "",
    var email: String? = null,
    var phone: String? = null,
    @Column(name = "department_id")
    var departmentId: Long? = null,
    @Column(name = "role_id", nullable = false)
    var roleId: Long = 0,
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.ACTIVE,
    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,
    @Column(name = "created_by")
    var createdBy: Long? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_by")
    var updatedBy: Long? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "job_position")
class JobPosition(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "department_id", nullable = false)
    var departmentId: Long = 0,
    @Column(name = "position_code", nullable = false, unique = true)
    var positionCode: String = "",
    @Column(name = "position_name", nullable = false)
    var positionName: String = "",
    var description: String? = null,
    @Column(name = "default_template_id")
    var defaultTemplateId: Long? = null,
    @Enumerated(EnumType.STRING)
    var status: RecordStatus = RecordStatus.ACTIVE,
    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_by", nullable = false)
    var updatedBy: Long = 0,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "assessment_template")
class AssessmentTemplate(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "template_name", nullable = false)
    var templateName: String = "",
    @Column(name = "position_id", nullable = false)
    var positionId: Long = 0,
    @Column(name = "owner_user_id", nullable = false)
    var ownerUserId: Long = 0,
    @Enumerated(EnumType.STRING)
    var status: TemplateStatus = TemplateStatus.DRAFT,
    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_by", nullable = false)
    var updatedBy: Long = 0,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "assessment_template_version")
class AssessmentTemplateVersion(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "template_id", nullable = false)
    var templateId: Long = 0,
    @Column(name = "version_no", nullable = false)
    var versionNo: Int = 1,
    @Column(name = "schema_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var schemaJson: String = "{}",
    @Enumerated(EnumType.STRING)
    @Column(name = "version_status", nullable = false)
    var versionStatus: TemplateVersionStatus = TemplateVersionStatus.DRAFT,
    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_by", nullable = false)
    var updatedBy: Long = 0,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Column(name = "submitted_by")
    var submittedBy: Long? = null,
    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,
    @Column(name = "published_by")
    var publishedBy: Long? = null,
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
)

@Entity
@Table(name = "assessment_task")
class AssessmentTask(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "task_no", nullable = false, unique = true)
    var taskNo: String = "",
    @Column(name = "candidate_name", nullable = false)
    var candidateName: String = "",
    @Column(name = "candidate_phone")
    var candidatePhone: String? = null,
    @Column(name = "candidate_email")
    var candidateEmail: String? = null,
    @Column(name = "candidate_source")
    var candidateSource: String? = null,
    @Column(name = "phone_bound_at")
    var phoneBoundAt: Instant? = null,
    @Column(name = "phone_verified_at")
    var phoneVerifiedAt: Instant? = null,
    @Column(name = "position_id", nullable = false)
    var positionId: Long = 0,
    @Column(name = "hr_user_id", nullable = false)
    var hrUserId: Long = 0,
    @Column(name = "template_version_id", nullable = false)
    var templateVersionId: Long = 0,
    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: String = "",
    @Column(nullable = false)
    var deadline: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TaskStatus = TaskStatus.DRAFT,
    @Column(name = "sent_at")
    var sentAt: Instant? = null,
    @Column(name = "opened_at")
    var openedAt: Instant? = null,
    @Column(name = "last_access_at")
    var lastAccessAt: Instant? = null,
    @Column(name = "draft_saved_at")
    var draftSavedAt: Instant? = null,
    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,
    @Column(name = "review_started_at")
    var reviewStartedAt: Instant? = null,
    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    @Column(name = "archived_at")
    var archivedAt: Instant? = null,
    @Version
    var version: Int = 0,
    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "assessment_assignment")
class AssessmentAssignment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "task_id", nullable = false)
    var taskId: Long = 0,
    @Column(name = "reviewer_user_id", nullable = false)
    var reviewerUserId: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AssignmentStatus = AssignmentStatus.WAITING_CANDIDATE,
    @Column(name = "assigned_by", nullable = false)
    var assignedBy: Long = 0,
    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Instant = Instant.now(),
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "assessment_answer")
class AssessmentAnswer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "task_id", nullable = false)
    var taskId: Long = 0,
    @Column(name = "question_id", nullable = false)
    var questionId: String = "",
    @Column(name = "answer_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var answerJson: String = "null",
    @Column(name = "draft_version", nullable = false)
    var draftVersion: Int = 1,
    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

enum class FileUploadStatus { UPLOADING, COMPLETED, FAILED, DELETED }

@Entity
@Table(name = "assessment_file")
class AssessmentFile(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "task_id", nullable = false)
    var taskId: Long = 0,
    @Column(name = "question_id", nullable = false)
    var questionId: String = "",
    @Column(name = "object_key", nullable = false, unique = true)
    var objectKey: String = "",
    @Column(name = "file_name", nullable = false)
    var fileName: String = "",
    @Column(name = "file_extension", nullable = false)
    var fileExtension: String = "",
    @Column(name = "content_type", nullable = false)
    var contentType: String = "",
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false)
    var uploadStatus: FileUploadStatus = FileUploadStatus.UPLOADING,
    var checksum: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

@Entity
@Table(name = "assessment_review")
class AssessmentReview(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "assignment_id", nullable = false, unique = true)
    var assignmentId: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var conclusion: ReviewConclusion = ReviewConclusion.RESERVED,
    var reason: String? = null,
    var score: java.math.BigDecimal? = null,
    @Column(name = "submitted_by", nullable = false)
    var submittedBy: Long = 0,
    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "operation_log")
class OperationLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "task_id")
    var taskId: Long? = null,
    @Column(name = "operator_type", nullable = false)
    var operatorType: String = "USER",
    @Column(name = "operator_id")
    var operatorId: Long? = null,
    @Column(nullable = false)
    var action: String = "",
    @Column(name = "from_status")
    var fromStatus: String? = null,
    @Column(name = "to_status")
    var toStatus: String? = null,
    @Column(name = "detail_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var detailJson: String? = null,
    var ip: String? = null,
    @Column(name = "user_agent")
    var userAgent: String? = null,
    @Column(name = "trace_id")
    var traceId: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
