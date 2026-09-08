package im.hikaru.ruoyi.module.system.controller.admin.logger

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog.LoginLogRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.LoginLogDO
import im.hikaru.ruoyi.module.system.service.logger.LoginLogService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/system/login-log")
@Validated
class LoginLogController(private val loginLogService: LoginLogService) {
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('system:login-log:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<LoginLogRespVO?> =
        CommonResult.success(loginLogService.getLoginLog(id)?.toResponse())

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:login-log:query')")
    fun page(@Valid req: LoginLogPageReqVO): CommonResult<PageResult<LoginLogRespVO>> {
        val result = loginLogService.getLoginLogPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:login-log:export')")
    fun export(response: HttpServletResponse, @Valid req: LoginLogPageReqVO) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val list = loginLogService.getLoginLogPage(req).list.map { it.toResponse() }
        ExcelUtils.write(response, "login-logs.xls", "data", LoginLogRespVO::class.java, list)
    }

    private fun LoginLogDO.toResponse() = LoginLogRespVO().apply {
        id = this@toResponse.id
        logType = this@toResponse.logType
        userId = this@toResponse.userId
        userType = this@toResponse.userType
        traceId = this@toResponse.traceId
        username = this@toResponse.username
        result = this@toResponse.result
        userIp = this@toResponse.userIp
        userAgent = this@toResponse.userAgent
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
