package im.hikaru.ruoyi.framework.common.biz.infra.logger

import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO
import org.springframework.scheduling.annotation.Async

/**
 * API 访问日志的 API 接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface ApiAccessLogCommonApi {

    /**
     * 创建 API 访问日志
     *
     * @param createDTO 创建信息
     */
    fun createApiAccessLog(createDTO: ApiAccessLogCreateReqDTO)

    /**
     * 【异步】创建 API 访问日志
     *
     * @param createDTO 访问日志 DTO
     */
    @Async
    fun createApiAccessLogAsync(createDTO: ApiAccessLogCreateReqDTO) {
        createApiAccessLog(createDTO)
    }
}
