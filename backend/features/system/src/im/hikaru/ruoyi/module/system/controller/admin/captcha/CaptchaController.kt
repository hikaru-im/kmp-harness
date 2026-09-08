package im.hikaru.ruoyi.module.system.controller.admin.captcha

import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import com.anji.captcha.model.common.ResponseModel
import com.anji.captcha.model.vo.CaptchaVO
import com.anji.captcha.service.CaptchaService
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController("adminCaptchaController")
@RequestMapping("/system/captcha")
class CaptchaController(private val captchaService: CaptchaService) {
    @PostMapping("/get")
    @PermitAll
    @TenantIgnore
    fun get(@RequestBody data: CaptchaVO, request: HttpServletRequest): ResponseModel {
        data.browserInfo = getRemoteId(request)
        return captchaService.get(data)
    }

    @PostMapping("/check")
    @PermitAll
    @TenantIgnore
    fun check(@RequestBody data: CaptchaVO, request: HttpServletRequest): ResponseModel {
        data.browserInfo = getRemoteId(request)
        return captchaService.check(data)
    }

    companion object {
        fun getRemoteId(request: HttpServletRequest): String =
            ServletUtils.getClientIP(request) + request.getHeader("user-agent").orEmpty()
    }
}
