package im.hikaru.ruoyi.module.infra.api.logger

import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiErrorLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import im.hikaru.ruoyi.module.infra.service.logger.ApiErrorLogService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

/**
 * API 错误日志的 API 实现类 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Service
@Validated
class ApiErrorLogApiImpl(
    private val apiErrorLogService: ApiErrorLogService,
) : ApiErrorLogCommonApi {

    override fun createApiErrorLog(createDTO: ApiErrorLogCreateReqDTO) {
        apiErrorLogService.createApiErrorLog(createDTO)
    }
}
