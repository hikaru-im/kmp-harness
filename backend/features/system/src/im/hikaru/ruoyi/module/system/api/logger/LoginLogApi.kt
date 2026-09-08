package im.hikaru.ruoyi.module.system.api.logger

import im.hikaru.ruoyi.module.system.api.logger.dto.LoginLogCreateReqDTO
import jakarta.validation.Valid

interface LoginLogApi {
    fun createLoginLog(@Valid reqDTO: LoginLogCreateReqDTO)
}
