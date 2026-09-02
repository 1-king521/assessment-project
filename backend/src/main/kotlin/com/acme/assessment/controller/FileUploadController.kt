package com.acme.assessment.controller

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/public/assessments/{token}/files")
class FileUploadController(private val service: FileUploadService) {
    @PostMapping("/presign")
    fun presign(
        @PathVariable token: String,
        @Valid @RequestBody request: PresignFileRequest,
    ) = service.presign(token, request)

    @PutMapping("/{fileId}/content", consumes = ["multipart/form-data"])
    fun upload(
        @PathVariable token: String,
        @PathVariable fileId: Long,
        @RequestParam("file") file: MultipartFile,
    ) = service.upload(token, fileId, file)

    @PostMapping("/{fileId}/complete")
    fun complete(
        @PathVariable token: String,
        @PathVariable fileId: Long,
        @Valid @RequestBody request: CompleteFileRequest,
    ) = service.complete(token, fileId, request)

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable token: String, @PathVariable fileId: Long) {
        service.delete(token, fileId)
    }

}
