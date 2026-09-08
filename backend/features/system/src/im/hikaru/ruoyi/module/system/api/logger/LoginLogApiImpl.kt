package im.hikaru.ruoyi.module.system.api.logger

import im.hikaru.ruoyi.module.system.api.logger.dto.LoginLogCreateReqDTO
import im.hikaru.ruoyi.module.system.service.logger.LoginLogService
import org.springframework.stereotype.Service

@Service
class LoginLogApiImpl(private val loginLogService: LoginLogService) : LoginLogApi {
    override fun createLoginLog(reqDTO: LoginLogCreateReqDTO) = loginLogService.createLoginLog(reqDTO)
}
