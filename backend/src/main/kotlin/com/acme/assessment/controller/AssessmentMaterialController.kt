package com.acme.assessment.controller

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.config.AppProperties
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.NotFoundException
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class AssessmentMaterialResponse(
    val name: String,
    val url: String,
    val kind: String,
)

@RestController
@RequestMapping("/api")
class AssessmentMaterialController(private val service: AssessmentMaterialService) {
    @PostMapping("/assessment-materials", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'REVIEWER', 'ADMIN')")
    fun upload(@RequestParam("file") file: MultipartFile) = service.store(file)

    @GetMapping("/public/assessment-materials/{storageName}")
    fun read(@PathVariable storageName: String): ResponseEntity<Resource> {
        val stored = service.load(storageName)
        return ResponseEntity.ok()
            .contentType(MediaTypeFactory.getMediaType(stored.resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(stored.fileName, StandardCharsets.UTF_8).build().toString(),
            )
            .body(stored.resource)
    }
}

data class StoredAssessmentMaterial(val fileName: String, val resource: Resource)

@Service
class AssessmentMaterialService(private val properties: AppProperties) {
    fun store(file: MultipartFile): AssessmentMaterialResponse {
        if (file.isEmpty) throw BusinessException("EMPTY_MATERIAL", "参考资料不能为空")
        if (file.size > properties.material.maxFileBytes) {
            throw BusinessException("MATERIAL_TOO_LARGE", "参考资料不能超过 ${properties.material.maxFileBytes} 字节")
        }
        val originalName = StringUtils.cleanPath(file.originalFilename ?: "material")
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val extension = originalName.substringAfterLast('.', "").lowercase()
        val allowed = properties.material.allowedExtensions.map { it.lowercase().removePrefix(".") }.toSet()
        if (extension.isBlank() || extension !in allowed) {
            val displayExtension = extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: "无扩展名"
            throw BusinessException("MATERIAL_EXTENSION_NOT_ALLOWED", "参考资料格式 $displayExtension 不支持")
        }
        val storageName = "${UUID.randomUUID()}.$extension"
        val target = safePath(storageName)
        try {
            Files.createDirectories(target.parent)
            file.inputStream.use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
        } catch (_: IOException) {
            throw BusinessException("MATERIAL_STORAGE_FAILED", "参考资料保存失败")
        }
        return AssessmentMaterialResponse(
            name = originalName,
            url = "/api/public/assessment-materials/$storageName",
            kind = if (file.contentType?.startsWith("image/") == true) "参考图片" else "参考文件",
        )
    }

    fun load(storageName: String): StoredAssessmentMaterial {
        if (!STORAGE_NAME.matches(storageName)) throw NotFoundException("参考资料")
        val target = safePath(storageName)
        if (!Files.isRegularFile(target)) throw NotFoundException("参考资料")
        return StoredAssessmentMaterial(storageName, UrlResource(target.toUri()))
    }

    private fun safePath(storageName: String): Path {
        val root = Path.of(properties.material.storagePath).toAbsolutePath().normalize()
        val target = root.resolve(storageName).normalize()
        if (!target.startsWith(root)) throw BusinessException("INVALID_MATERIAL_PATH", "参考资料路径不合法")
        return target
    }

    private companion object {
        val STORAGE_NAME = Regex("^[0-9a-fA-F-]{36}\\.[A-Za-z0-9]+$")
    }
}
