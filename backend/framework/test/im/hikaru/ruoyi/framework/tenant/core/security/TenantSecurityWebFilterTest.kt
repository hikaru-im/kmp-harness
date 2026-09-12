package im.hikaru.ruoyi.framework.tenant.core.security

import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiErrorLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.framework.tenant.config.TenantProperties
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.service.TenantFrameworkService
import im.hikaru.ruoyi.framework.web.config.WebProperties
import im.hikaru.ruoyi.framework.web.core.handler.GlobalExceptionHandler
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class TenantSecurityWebFilterTest {
    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun matchingAuthenticatedTenantIsValidatedAndAllowed() {
        val tenantService = RecordingTenantService()
        val request = request()
        TenantContextHolder.setTenantId(7L)
        SecurityFrameworkUtils.setLoginUser(loginUser(7L), request)
        var reachedEndpoint = false

        filter(tenantService).doFilter(request, MockHttpServletResponse(), FilterChain { _, _ ->
            reachedEndpoint = true
        })

        assertTrue(reachedEndpoint)
        assertEquals(listOf(7L), tenantService.validated)
    }

    @Test
    fun authenticatedUserCannotCrossTenantBoundary() {
        val tenantService = RecordingTenantService()
        val request = request()
        val response = MockHttpServletResponse()
        TenantContextHolder.setTenantId(8L)
        SecurityFrameworkUtils.setLoginUser(loginUser(7L), request)
        var reachedEndpoint = false

        filter(tenantService).doFilter(request, response, FilterChain { _, _ ->
            reachedEndpoint = true
        })

        assertFalse(reachedEndpoint)
        assertTrue(response.contentAsString.contains("\"code\":403"))
        assertTrue(tenantService.validated.isEmpty())
    }

    @Test
    fun unknownTenantIsRejectedBeforeEndpointExecution() {
        val tenantService = RecordingTenantService(rejectedTenant = 8L)
        val request = request()
        val response = MockHttpServletResponse()
        TenantContextHolder.setTenantId(8L)
        var reachedEndpoint = false

        filter(tenantService).doFilter(request, response, FilterChain { _, _ ->
            reachedEndpoint = true
        })

        assertFalse(reachedEndpoint)
        assertTrue(response.contentAsString.contains("\"code\":1002015000"))
        assertEquals(listOf(8L), tenantService.validated)
    }

    private fun filter(tenantService: TenantFrameworkService) = TenantSecurityWebFilter(
        WebProperties(),
        TenantProperties(),
        emptySet(),
        GlobalExceptionHandler("tenant-test", object : ApiErrorLogCommonApi {
            override fun createApiErrorLog(createDTO: ApiErrorLogCreateReqDTO) = Unit
        }),
        tenantService,
    )

    private fun request() = MockHttpServletRequest().apply {
        requestURI = "/app-api/member/profile/get"
    }

    private fun loginUser(tenantId: Long) = LoginUser().apply {
        id = 17L
        userType = 2
        this.tenantId = tenantId
    }

    private class RecordingTenantService(
        private val rejectedTenant: Long? = null,
    ) : TenantFrameworkService {
        val validated = mutableListOf<Long>()

        override fun getTenantIds(): List<Long> = emptyList()

        override fun validTenant(id: Long) {
            validated += id
            if (id == rejectedTenant) {
                throw ServiceException(1_002_015_000, "Tenant does not exist")
            }
        }
    }
}
