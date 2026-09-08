package im.hikaru.ruoyi.module.infra.service.logger

import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.logger.ApiAccessLogDO

/**
 * API 访问日志 Service 接口 (迁移自 Java)
 * @author 芋道源码
 */
interface ApiAccessLogService {

    fun createApiAccessLog(createDTO: ApiAccessLogCreateReqDTO)

    fun getApiAccessLog(id: Long): ApiAccessLogDO?

    fun getApiAccessLogPage(pageReqVO: ApiAccessLogPageReqVO): PageResult<ApiAccessLogDO>

    fun cleanAccessLog(exceedDay: Int, deleteLimit: Int): Int
}
