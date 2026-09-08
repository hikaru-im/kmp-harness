package im.hikaru.ruoyi.framework.security.config

import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiErrorLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiAccessLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.biz.system.dict.DictDataCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.dict.dto.DictDataRespDTO
import im.hikaru.ruoyi.framework.common.biz.system.tenant.TenantCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.logger.OperateLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.OAuth2TokenCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO
import im.hikaru.ruoyi.framework.common.biz.system.permission.PermissionCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO
import im.hikaru.ruoyi.framework.security.core.context.TransmittableThreadLocalSecurityContextHolderStrategy
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties
import com.mzt.logapi.service.ILogRecordService
import jakarta.annotation.security.PermitAll
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(
    classes = [SecurityFilterChainTest.TestApplication::class],
    properties = [
        "spring.application.name=security-test",
        "yudao.web.admin-ui.url=http://localhost",
        "yudao.swagger.title=Security Test",
        "yudao.swagger.description=Security test API",
        "yudao.swagger.author=Yudao",
        "yudao.swagger.version=1.0.0",
        "yudao.swagger.url=https://example.com",
        "yudao.swagger.email=test@example.com",
        "yudao.swagger.license=MIT",
        "yudao.swagger.license-url=https://example.com/license",
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
    ],
)
class SecurityFilterChainTest {

    @Autowired
    private lateinit var applicationContext: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: SecurityFilterChain

    @Autowired
    private lateinit var logRecordService: ILogRecordService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val builder = MockMvcBuilders.webAppContextSetup(applicationContext)
        builder.apply<DefaultMockMvcBuilder>(springSecurity())
        mockMvc = builder.build()
    }

    @Test
    fun `permit all annotations are applied to the security chain`() {
        assertThat(securityFilterChain).isNotNull()
        mockMvc.get("/public").andExpect {
            status { isOk() }
            content { string("public") }
        }
    }

    @Test
    fun `unannotated endpoints require authentication`() {
        mockMvc.get("/private").andExpect {
            content { jsonPath("$.code") { value(401) } }
        }
    }

    @Test
    fun `security context uses the transmittable thread local strategy`() {
        assertThat(SecurityContextHolder.getContextHolderStrategy())
            .isInstanceOf(TransmittableThreadLocalSecurityContextHolderStrategy::class.java)
    }

    @Test
    fun `bizlog uses the yudao operation log service`() {
        assertThat(logRecordService)
            .isInstanceOf(im.hikaru.ruoyi.framework.operatelog.core.service.LogRecordServiceImpl::class.java)
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    class TestApplication {
        @Bean
        fun testController(): TestController = TestController()

        @Bean
        fun oauth2TokenCommonApi(): OAuth2TokenCommonApi = TestOAuth2TokenCommonApi()

        @Bean
        fun permissionCommonApi(): PermissionCommonApi = TestPermissionCommonApi()

        @Bean
        fun apiErrorLogCommonApi(): ApiErrorLogCommonApi = TestApiErrorLogCommonApi()

        @Bean
        fun apiAccessLogCommonApi(): ApiAccessLogCommonApi = TestApiAccessLogCommonApi()

        @Bean
        fun operateLogCommonApi(): OperateLogCommonApi = TestOperateLogCommonApi()

        @Bean
        fun dictDataCommonApi(): DictDataCommonApi = TestDictDataCommonApi()

        @Bean
        fun redissonClient(): RedissonClient = mock(RedissonClient::class.java)

        @Bean
        fun tenantCommonApi(): TenantCommonApi = TestTenantCommonApi()

        @Bean
        fun knife4jProperties(): Knife4jProperties = Knife4jProperties()

        open class TestOAuth2TokenCommonApi : OAuth2TokenCommonApi {
            override fun createAccessToken(req: OAuth2AccessTokenCreateReqDTO): OAuth2AccessTokenRespDTO =
                unsupported()

            override fun checkAccessToken(accessToken: String): OAuth2AccessTokenCheckRespDTO = unsupported()

            override fun removeAccessToken(accessToken: String): OAuth2AccessTokenRespDTO? = unsupported()

            override fun refreshAccessToken(refreshToken: String, clientId: String): OAuth2AccessTokenRespDTO =
                unsupported()

            private fun <T> unsupported(): T = throw UnsupportedOperationException("Not used by this test")
        }

        open class TestPermissionCommonApi : PermissionCommonApi {
            override fun hasAnyPermissions(userId: Long, vararg permissions: String): Boolean = false

            override fun hasAnyRoles(userId: Long, vararg roles: String): Boolean = false

            override fun getDeptDataPermission(userId: Long): DeptDataPermissionRespDTO? = null
        }

        open class TestApiErrorLogCommonApi : ApiErrorLogCommonApi {
            override fun createApiErrorLog(createDTO: ApiErrorLogCreateReqDTO) = Unit
        }

        open class TestApiAccessLogCommonApi : ApiAccessLogCommonApi {
            override fun createApiAccessLog(createDTO: ApiAccessLogCreateReqDTO) = Unit
        }

        open class TestOperateLogCommonApi : OperateLogCommonApi {
            override fun createOperateLog(createReqDTO: OperateLogCreateReqDTO) = Unit
        }

        open class TestDictDataCommonApi : DictDataCommonApi {
            override fun getDictDataList(dictType: String): List<DictDataRespDTO> = emptyList()
        }

        open class TestTenantCommonApi : TenantCommonApi {
            override fun getTenantIdList(): List<Long> = emptyList()

            override fun validateTenant(id: Long) = Unit
        }
    }

    @RestController
    class TestController {
        @PermitAll
        @GetMapping("/public")
        fun publicEndpoint(): String = "public"

        @GetMapping("/private")
        fun privateEndpoint(): String = "private"
    }
}
