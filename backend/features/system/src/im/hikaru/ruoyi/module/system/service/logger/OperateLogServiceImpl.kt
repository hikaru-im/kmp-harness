package im.hikaru.ruoyi.module.system.service.logger

import im.hikaru.ruoyi.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO
import im.hikaru.ruoyi.module.system.api.logger.dto.OperateLogPageReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.OperateLogDO
import im.hikaru.ruoyi.module.system.dal.mysql.logger.OperateLogDao
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class OperateLogServiceImpl : OperateLogService {
    override fun createOperateLog(req: OperateLogCreateReqDTO) {
        OperateLogDao.insert(OperateLogDO().apply {
            traceId = req.traceId
            userId = req.userId
            userType = req.userType
            type = req.type
            subType = req.subType
            bizId = req.bizId
            action = req.action
            extra = req.extra
            requestMethod = req.requestMethod
            requestUrl = req.requestUrl
            userIp = req.userIp
            userAgent = req.userAgent
        })
    }

    override fun getOperateLog(id: Long) = OperateLogDao.selectById(id)

    override fun getOperateLogPage(req: OperateLogPageReqVO) = OperateLogDao.selectPage(req)

    override fun getOperateLogPage(req: OperateLogPageReqDTO) = OperateLogDao.selectPage(req)
}
