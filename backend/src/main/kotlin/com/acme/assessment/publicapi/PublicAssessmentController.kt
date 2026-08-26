package com.acme.assessment.publicapi

import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest
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
    fun draft(@PathVariable token: String, request: HttpServletRequest) =
        service.getDraft(token, request.cookies?.firstOrNull { it.name == service.cookieName() }?.value)

    @PutMapping("/draft")
    fun saveDraft(@PathVariable token: String, @Valid @RequestBody request: SaveDraftRequest, httpRequest: HttpServletRequest) =
        service.saveDraft(token, httpRequest.cookies?.firstOrNull { it.name == service.cookieName() }?.value, request)

    @PostMapping("/submit")
    fun submit(@PathVariable token: String, @Valid @RequestBody request: SubmitAssessmentRequest, httpRequest: HttpServletRequest) =
        service.submit(token, httpRequest.cookies?.firstOrNull { it.name == service.cookieName() }?.value, request)
}
