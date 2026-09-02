package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.config.AppProperties
import com.acme.assessment.entity.AssessmentFile
import com.acme.assessment.entity.FileUploadStatus
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.repository.AssessmentFileRepository
import com.acme.assessment.repository.OperationLogRepository
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class FileStorageService(
    private val fileRepository: AssessmentFileRepository,
    private val operationLogRepository: OperationLogRepository,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    fun createRecord(taskId: Long, request: PresignFileRequest): AssessmentFile {
        val originalName = Path.of(request.fileName).fileName.toString()
        val extension = originalName.substringAfterLast('.', "").lowercase()
        if (extension.isBlank() || extension !in allowedExtensions()) {
            throw BusinessException("FILE_EXTENSION_NOT_ALLOWED", "文件格式不支持")
        }
        if (request.sizeBytes > properties.candidate.maxFileBytes) {
            throw BusinessException("FILE_TOO_LARGE", "文件不能超过 ${properties.candidate.maxFileBytes} 字节")
        }
        val objectKey = "$taskId/${UUID.randomUUID()}.$extension"
        return fileRepository.save(AssessmentFile(
            taskId = taskId,
            questionId = request.questionId.trim(),
            objectKey = objectKey,
            fileName = originalName,
            fileExtension = extension,
            contentType = request.contentType.trim().lowercase(),
            sizeBytes = request.sizeBytes,
            uploadStatus = FileUploadStatus.UPLOADING,
            createdAt = clock.instant(),
        ))
    }

    fun storeContent(file: AssessmentFile, multipart: MultipartFile): FileUploadResponse {
        if (file.uploadStatus != FileUploadStatus.UPLOADING) {
            throw ConflictException("FILE_NOT_UPLOADING", "文件当前不允许上传")
        }
        if (multipart.isEmpty) throw BusinessException("EMPTY_FILE", "不能上传空文件")
        if (multipart.size > properties.candidate.maxFileBytes) throw BusinessException("FILE_TOO_LARGE", "文件超过大小限制")
        val target = safePath(file.objectKey)
        try {
            Files.createDirectories(target.parent)
            multipart.inputStream.use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
        } catch (exception: IOException) {
            file.uploadStatus = FileUploadStatus.FAILED
            fileRepository.save(file)
            throw BusinessException("FILE_STORAGE_FAILED", "文件保存失败")
        }
        file.sizeBytes = multipart.size
        file.checksum = sha256(target)
        file.uploadStatus = FileUploadStatus.COMPLETED
        file.completedAt = clock.instant()
        val saved = fileRepository.save(file)
        operationLogRepository.save(OperationLog(
            taskId = file.taskId,
            operatorType = "CANDIDATE",
            action = "FILE_UPLOADED",
            detailJson = "{\"fileId\":${file.id}}",
            createdAt = clock.instant(),
        ))
        return saved.toResponse()
    }

    fun complete(file: AssessmentFile, checksum: String): FileUploadResponse {
        val target = safePath(file.objectKey)
        if (!Files.exists(target)) throw ConflictException("FILE_CONTENT_MISSING", "文件内容尚未上传")
        val actual = sha256(target)
        if (!actual.equals(checksum.trim(), true)) {
            file.uploadStatus = FileUploadStatus.FAILED
            fileRepository.save(file)
            throw BusinessException("FILE_CHECKSUM_MISMATCH", "文件校验失败")
        }
        file.checksum = actual
        file.sizeBytes = Files.size(target)
        if (file.sizeBytes > properties.candidate.maxFileBytes) throw BusinessException("FILE_TOO_LARGE", "文件超过大小限制")
        file.uploadStatus = FileUploadStatus.COMPLETED
        file.completedAt = clock.instant()
        return fileRepository.save(file).toResponse()
    }

    fun delete(file: AssessmentFile) {
        val target = safePath(file.objectKey)
        Files.deleteIfExists(target)
        file.uploadStatus = FileUploadStatus.DELETED
        file.deletedAt = clock.instant()
        fileRepository.save(file)
        operationLogRepository.save(OperationLog(
            taskId = file.taskId,
            operatorType = "CANDIDATE",
            action = "FILE_DELETED",
            detailJson = "{\"fileId\":${file.id}}",
            createdAt = clock.instant(),
        ))
    }

    fun findForTask(fileId: Long, taskId: Long): AssessmentFile {
        val file = fileRepository.findById(fileId).orElseThrow { NotFoundException("测评文件") }
        if (file.taskId != taskId) throw NotFoundException("测评文件")
        return file
    }

    fun loadContent(file: AssessmentFile): Resource {
        val target = safePath(file.objectKey)
        if (!Files.isRegularFile(target)) throw NotFoundException("测评文件内容")
        return UrlResource(target.toUri())
    }

    fun hasIncompleteFiles(taskId: Long): Boolean = fileRepository.findAllByTaskId(taskId)
        .any { it.uploadStatus == FileUploadStatus.UPLOADING }

    private fun allowedExtensions() = properties.candidate.allowedExtensions.map { it.lowercase().removePrefix(".") }.toSet()

    private fun safePath(objectKey: String): Path {
        val root = Path.of(properties.candidate.storagePath).toAbsolutePath().normalize()
        val target = root.resolve(objectKey).normalize()
        if (!target.startsWith(root)) throw BusinessException("INVALID_OBJECT_KEY", "文件路径不合法")
        return target
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun AssessmentFile.toResponse() = FileUploadResponse(
        fileId = requireNotNull(id),
        fileName = fileName,
        sizeBytes = sizeBytes,
        uploadStatus = uploadStatus.name,
        completedAt = completedAt,
    )
}
