package com.acme.assessment.service

import com.acme.assessment.config.AppProperties
import com.acme.assessment.config.BootstrapProperties
import com.acme.assessment.config.DingTalkProperties
import com.acme.assessment.config.JwtProperties
import com.acme.assessment.entity.User
import com.acme.assessment.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class DingTalkUserServiceTest {
    private val userRepository = mock<UserRepository>()
    private val now = Instant.parse("2026-08-29T06:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val properties = AppProperties(
        jwt = JwtProperties("issuer", "secret-secret-secret-secret-1234", Duration.ofHours(8)),
        publicBaseUrl = "http://localhost/assessment",
        bootstrap = BootstrapProperties("admin", "ChangeMe123!"),
        dingtalk = DingTalkProperties(
            enabled = true,
            clientId = "cid",
            clientSecret = "csecret",
            robotCode = "robot",
        ),
    )

    private fun newService(): Pair<DingTalkUserService, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        return DingTalkUserService(properties, userRepository, builder, clock) to server
    }

    private fun user() = User(
        id = 7, username = "hr", realName = "招聘专员",
        passwordHash = "x", roleId = 1, phone = "13800001111",
    )

    @Test
    fun `resolves userid from department user list`() {
        val (service, server) = newService()
        // 1. Mock accessToken
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("""{"accessToken":"tok"}""", MediaType.APPLICATION_JSON))

        // 2. Mock 获取部门列表（根部门1，没有子部门）
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=tok"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"dept_id_list":[]}}""",
                MediaType.APPLICATION_JSON,
            ))

        // 3. Mock 获取部门1的用户列表，找到目标用户
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/list?access_token=tok"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"list":[{"userid":"zhangsan","name":"张三","mobile":"13800001111"}]}}""",
                MediaType.APPLICATION_JSON,
            ))

        val target = user()
        val userId = service.resolveAndCache(target)

        assertThat(userId).isEqualTo("zhangsan")
        assertThat(target.dingtalkUserId).isEqualTo("zhangsan")
        assertThat(target.dingtalkName).isEqualTo("张三")
        assertThat(target.dingtalkMatchStatus).isEqualTo("MATCHED")
        assertThat(target.dingtalkMatchedAt).isEqualTo(now)
        assertThat(target.dingtalkLastError).isNull()
        server.verify()
    }

    @Test
    fun `strips country code and separators before querying departments`() {
        val (service, server) = newService()
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("""{"accessToken":"tok"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"dept_id_list":[]}}""",
                MediaType.APPLICATION_JSON,
            ))
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/list?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"list":[{"userid":"zhangsan","mobile":"13800001111"}]}}""",
                MediaType.APPLICATION_JSON,
            ))

        val target = user().apply { phone = "+86 138-0000 1111" }
        assertThat(service.resolveAndCache(target)).isEqualTo("zhangsan")
        // 缓存命中判断也要用归一化后的号码，否则每条通知都会重新查钉钉
        assertThat(target.dingtalkMatchedMobile).isEqualTo("13800001111")
        server.verify()
    }

    @Test
    fun `marks not found when user not in any department`() {
        val (service, server) = newService()
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("""{"accessToken":"tok"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"dept_id_list":[]}}""",
                MediaType.APPLICATION_JSON,
            ))
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/list?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"list":[{"userid":"lisi","mobile":"13900000000"}]}}""",
                MediaType.APPLICATION_JSON,
            ))

        val target = user()
        assertThat(service.resolveAndCache(target)).isNull()
        assertThat(target.dingtalkMatchStatus).isEqualTo("NOT_FOUND")
        assertThat(target.dingtalkLastError).contains("钉钉通讯录中未找到手机号")
        server.verify()
    }

    @Test
    fun `searches across multiple departments`() {
        val (service, server) = newService()
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("""{"accessToken":"tok"}""", MediaType.APPLICATION_JSON))

        // 根部门1有子部门2和3
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"dept_id_list":[2,3]}}""",
                MediaType.APPLICATION_JSON,
            ))

        // 部门2没有子部门
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"dept_id_list":[]}}""",
                MediaType.APPLICATION_JSON,
            ))

        // 部门3没有子部门
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"dept_id_list":[]}}""",
                MediaType.APPLICATION_JSON,
            ))

        // 部门1的用户列表，没找到
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/list?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"list":[{"userid":"lisi","mobile":"13900000000"}]}}""",
                MediaType.APPLICATION_JSON,
            ))

        // 部门2的用户列表，找到了目标用户
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/list?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"list":[{"userid":"zhangsan","name":"张三","mobile":"13800001111"}]}}""",
                MediaType.APPLICATION_JSON,
            ))

        val target = user()
        val userId = service.resolveAndCache(target)

        assertThat(userId).isEqualTo("zhangsan")
        assertThat(target.dingtalkMatchStatus).isEqualTo("MATCHED")
        server.verify()
    }

    @Test
    fun `marks failure when department list returns error`() {
        val (service, server) = newService()
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("""{"accessToken":"tok"}""", MediaType.APPLICATION_JSON))

        // 获取子部门失败，但根部门1仍然会被添加到列表
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":400002,"errmsg":"无效的参数"}""",
                MediaType.APPLICATION_JSON,
            ))

        // 仍会查询根部门1的用户列表，但找不到
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/list?access_token=tok"))
            .andRespond(withSuccess(
                """{"errcode":0,"errmsg":"ok","result":{"list":[]}}""",
                MediaType.APPLICATION_JSON,
            ))

        val target = user()
        assertThat(service.resolveAndCache(target)).isNull()
        assertThat(target.dingtalkMatchStatus).isEqualTo("NOT_FOUND")
        assertThat(target.dingtalkLastError).contains("钉钉通讯录中未找到手机号")
        server.verify()
    }
}
