package im.hikaru.ruoyi.module.system.service.logger

import im.hikaru.ruoyi.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.api.logger.dto.OperateLogPageReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.OperateLogDO

interface OperateLogService {
    fun createOperateLog(req: OperateLogCreateReqDTO)
    fun getOperateLog(id: Long): OperateLogDO?
    fun getOperateLogPage(req: OperateLogPageReqVO): PageResult<OperateLogDO>
    fun getOperateLogPage(req: OperateLogPageReqDTO): PageResult<OperateLogDO>
}
