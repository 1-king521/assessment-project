package com.acme.assessment.publicapi

import com.acme.assessment.domain.AssessmentFile
import com.acme.assessment.domain.TaskStatus
import com.acme.assessment.web.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.time.Instant

@Service
class FileUploadService(
    private val sessions: CandidateSessionService,
    private val storage: FileStorageService,
    private val clock: Clock,
) {
    fun cookieName(): String = sessions.cookieName()
    @Transactional
    fun presign(token: String, sessionId: String?, request: PresignFileRequest): PresignFileResponse {
        val task = sessions.requireSession(token, sessionId)
        rejectClosed(task.status)
        val file = storage.createRecord(requireNotNull(task.id), request)
        val expiresAt = clock.instant().plusSeconds(900)
        return PresignFileResponse(
            fileId = requireNotNull(file.id),
            objectKey = file.objectKey,
            uploadUrl = "/api/public/assessments/$token/files/${file.id}/content",
            expiresAt = expiresAt,
            uploadStatus = file.uploadStatus.name,
        )
    }

    @Transactional
    fun upload(token: String, sessionId: String?, fileId: Long, multipart: MultipartFile): FileUploadResponse {
        val task = sessions.requireSession(token, sessionId)
        rejectClosed(task.status)
        return storage.storeContent(storage.findForTask(fileId, requireNotNull(task.id)), multipart)
    }

    @Transactional
    fun complete(token: String, sessionId: String?, fileId: Long, request: CompleteFileRequest): FileUploadResponse {
        val task = sessions.requireSession(token, sessionId)
        rejectClosed(task.status)
        val file = storage.findForTask(fileId, requireNotNull(task.id))
        return storage.complete(file, request.checksum)
    }

    @Transactional
    fun delete(token: String, sessionId: String?, fileId: Long) {
        val task = sessions.requireSession(token, sessionId)
        rejectClosed(task.status)
        storage.delete(storage.findForTask(fileId, requireNotNull(task.id)))
    }

    private fun rejectClosed(status: TaskStatus) {
        if (status == TaskStatus.SUBMITTED || status == TaskStatus.REVIEWING || status == TaskStatus.REVIEWED) {
            throw BusinessException("TASK_ALREADY_SUBMITTED", "测评已经提交，不能修改文件")
        }
    }
}
