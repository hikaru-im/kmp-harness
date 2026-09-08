package im.hikaru.ruoyi.module.system.controller.admin.logger

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.operatelog.OperateLogRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.OperateLogDO
import im.hikaru.ruoyi.module.system.service.logger.OperateLogService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
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
@RequestMapping("/system/operate-log")
@Validated
class OperateLogController(
    private val operateLogService: OperateLogService,
    private val adminUserService: AdminUserService,
) {
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('system:operate-log:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<OperateLogRespVO?> {
        val log = operateLogService.getOperateLog(id)
        val userName = log?.userId?.let { adminUserService.getUser(it)?.nickname }
        return CommonResult.success(log?.toResponse(userName))
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:operate-log:query')")
    fun page(@Valid req: OperateLogPageReqVO): CommonResult<PageResult<OperateLogRespVO>> {
        val result = operateLogService.getOperateLogPage(req)
        return CommonResult.success(result.toResponsePage())
    }

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:operate-log:export')")
    fun export(response: HttpServletResponse, @Valid req: OperateLogPageReqVO) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        ExcelUtils.write(
            response,
            "operate-logs.xls",
            "data",
            OperateLogRespVO::class.java,
            operateLogService.getOperateLogPage(req).toResponsePage().list,
        )
    }

    private fun PageResult<OperateLogDO>.toResponsePage(): PageResult<OperateLogRespVO> {
        val users = adminUserService.getUserList(list.mapNotNull { it.userId }.toSet()).associateBy { it.id }
        return PageResult(total, list.map { log -> log.toResponse(users[log.userId]?.nickname) })
    }

    private fun OperateLogDO.toResponse(name: String?) = OperateLogRespVO().apply {
        id = this@toResponse.id
        traceId = this@toResponse.traceId
        userId = this@toResponse.userId
        userName = name
        userType = this@toResponse.userType
        type = this@toResponse.type
        subType = this@toResponse.subType
        bizId = this@toResponse.bizId
        action = this@toResponse.action
        extra = this@toResponse.extra
        requestMethod = this@toResponse.requestMethod
        requestUrl = this@toResponse.requestUrl
        userIp = this@toResponse.userIp
        userAgent = this@toResponse.userAgent
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
