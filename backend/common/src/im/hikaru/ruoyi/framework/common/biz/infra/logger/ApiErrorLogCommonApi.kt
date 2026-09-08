package im.hikaru.ruoyi.framework.common.biz.infra.logger

import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import org.springframework.scheduling.annotation.Async

/**
 * API 错误日志的 API 接口 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
interface ApiErrorLogCommonApi {

    /**
     * 创建 API 错误日志
     *
     * @param createDTO 创建信息
     */
    fun createApiErrorLog(createDTO: ApiErrorLogCreateReqDTO)

    /**
     * 【异步】创建 API 异常日志
     *
     * @param createDTO 异常日志 DTO
     */
    @Async
    fun createApiErrorLogAsync(createDTO: ApiErrorLogCreateReqDTO) {
        createApiErrorLog(createDTO)
    }
}
