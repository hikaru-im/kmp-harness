package im.hikaru.ruoyi.module.infra.api.logger

import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiAccessLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO
import im.hikaru.ruoyi.module.infra.service.logger.ApiAccessLogService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

/**
 * API 访问日志的 API 实现类 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Service
@Validated
class ApiAccessLogApiImpl(
    private val apiAccessLogService: ApiAccessLogService,
) : ApiAccessLogCommonApi {

    override fun createApiAccessLog(createDTO: ApiAccessLogCreateReqDTO) {
        apiAccessLogService.createApiAccessLog(createDTO)
    }
}
