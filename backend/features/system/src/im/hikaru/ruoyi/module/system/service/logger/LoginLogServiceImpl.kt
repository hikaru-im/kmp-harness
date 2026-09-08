package im.hikaru.ruoyi.module.system.service.logger

import im.hikaru.ruoyi.module.system.api.logger.dto.LoginLogCreateReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.LoginLogDO
import im.hikaru.ruoyi.module.system.dal.mysql.logger.LoginLogDao
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class LoginLogServiceImpl : LoginLogService {
    override fun getLoginLog(id: Long) = LoginLogDao.selectById(id)
    override fun getLoginLogPage(req: LoginLogPageReqVO) = LoginLogDao.selectPage(req)
    override fun createLoginLog(req: LoginLogCreateReqDTO) {
        LoginLogDao.insert(LoginLogDO().apply {
            logType = req.logType
            traceId = req.traceId
            userId = req.userId
            userType = req.userType
            username = req.username
            result = req.result
            userIp = req.userIp
            userAgent = req.userAgent
        })
    }
}
