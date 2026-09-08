package im.hikaru.ruoyi.module.system.api.logger

import im.hikaru.ruoyi.framework.common.biz.system.logger.OperateLogCommonApi
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.api.logger.dto.OperateLogPageReqDTO
import im.hikaru.ruoyi.module.system.api.logger.dto.OperateLogRespDTO

interface OperateLogApi : OperateLogCommonApi {
    fun getOperateLogPage(pageReqDTO: OperateLogPageReqDTO): PageResult<OperateLogRespDTO>
}
