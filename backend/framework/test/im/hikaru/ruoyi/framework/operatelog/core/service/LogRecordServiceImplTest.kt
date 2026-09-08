package im.hikaru.ruoyi.framework.operatelog.core.service

import im.hikaru.ruoyi.framework.common.biz.system.logger.OperateLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO
import im.hikaru.ruoyi.framework.security.core.LoginUser
import com.mzt.logapi.beans.LogRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class LogRecordServiceImplTest {

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `record maps bizlog user and request fields`() {
        var captured: OperateLogCreateReqDTO? = null
        val service = LogRecordServiceImpl(object : OperateLogCommonApi {
            override fun createOperateLog(createReqDTO: OperateLogCreateReqDTO) {
                captured = createReqDTO
            }
        })
        val loginUser = LoginUser().apply {
            id = 7L
            userType = 1
        }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(loginUser, null, emptyList())
        val servletRequest = MockHttpServletRequest("POST", "/admin-api/system/users/42").apply {
            remoteAddr = "192.0.2.10"
            addHeader("User-Agent", "migration-test")
        }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(servletRequest))

        service.record(LogRecord().apply {
            type = "SYSTEM_USER"
            subType = "UPDATE_USER"
            bizNo = "42"
            action = "Updated user 42"
            extra = "{\"source\":\"test\"}"
        })

        assertThat(captured).isNotNull
        assertThat(captured!!.userId).isEqualTo(7L)
        assertThat(captured!!.userType).isEqualTo(1)
        assertThat(captured!!.type).isEqualTo("SYSTEM_USER")
        assertThat(captured!!.subType).isEqualTo("UPDATE_USER")
        assertThat(captured!!.bizId).isEqualTo(42L)
        assertThat(captured!!.action).isEqualTo("Updated user 42")
        assertThat(captured!!.extra).isEqualTo("{\"source\":\"test\"}")
        assertThat(captured!!.requestMethod).isEqualTo("POST")
        assertThat(captured!!.requestUrl).isEqualTo("/admin-api/system/users/42")
        assertThat(captured!!.userIp).isEqualTo("192.0.2.10")
        assertThat(captured!!.userAgent).isEqualTo("migration-test")
    }
}
