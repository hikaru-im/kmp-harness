package im.hikaru.ruoyi.module.system.api.logger

import im.hikaru.ruoyi.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.api.logger.dto.OperateLogPageReqDTO
import im.hikaru.ruoyi.module.system.api.logger.dto.OperateLogRespDTO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.OperateLogDO
import im.hikaru.ruoyi.module.system.service.logger.OperateLogService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class OperateLogApiImpl(
    private val operateLogService: OperateLogService,
    private val adminUserService: AdminUserService,
) : OperateLogApi {
    override fun createOperateLog(createReqDTO: OperateLogCreateReqDTO) =
        operateLogService.createOperateLog(createReqDTO)

    override fun getOperateLogPage(pageReqDTO: OperateLogPageReqDTO): PageResult<OperateLogRespDTO> {
        val page = operateLogService.getOperateLogPage(pageReqDTO)
        val users = adminUserService.getUserList(page.list.mapNotNull { it.userId }.toSet())
            .associateBy { it.id }
        return PageResult(page.total, page.list.map { log ->
            log.toResponse(users[log.userId]?.nickname)
        })
    }

    private fun OperateLogDO.toResponse(name: String?) = OperateLogRespDTO().apply {
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
