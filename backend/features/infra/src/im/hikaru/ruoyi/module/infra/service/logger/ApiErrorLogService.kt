package im.hikaru.ruoyi.module.infra.service.logger

import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.logger.ApiErrorLogDO

/**
 * API 错误日志 Service 接口 (迁移自 Java)
 * @author 芋道源码
 */
interface ApiErrorLogService {

    fun createApiErrorLog(createDTO: ApiErrorLogCreateReqDTO)

    fun updateApiErrorLogProcess(id: Long, processStatus: Int, processUserId: Long?)

    fun getApiErrorLog(id: Long): ApiErrorLogDO?

    fun getApiErrorLogPage(pageReqVO: ApiErrorLogPageReqVO): PageResult<ApiErrorLogDO>

    fun cleanErrorLog(exceedDay: Int, deleteLimit: Int): Int
}
