package im.hikaru.ruoyi.framework.common.biz.system.logger

import im.hikaru.ruoyi.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO
import jakarta.validation.Valid
import org.springframework.scheduling.annotation.Async

interface OperateLogCommonApi {
    fun createOperateLog(@Valid createReqDTO: OperateLogCreateReqDTO)

    @Async
    fun createOperateLogAsync(createReqDTO: OperateLogCreateReqDTO) {
        createOperateLog(createReqDTO)
    }
}
