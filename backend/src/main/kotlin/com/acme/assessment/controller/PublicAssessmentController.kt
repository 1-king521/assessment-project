package com.acme.assessment.controller

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public/assessments/{token}")
class PublicAssessmentController(private val service: PublicAssessmentService) {
    @GetMapping
    fun open(@PathVariable token: String) = service.open(token)

    @GetMapping("/draft")
    fun draft(@PathVariable token: String) = service.getDraft(token)

    @PutMapping("/draft")
    fun saveDraft(@PathVariable token: String, @Valid @RequestBody request: SaveDraftRequest) = service.saveDraft(token, request)

    @PostMapping("/submit")
    fun submit(@PathVariable token: String, @Valid @RequestBody request: SubmitAssessmentRequest) = service.submit(token, request)
}
