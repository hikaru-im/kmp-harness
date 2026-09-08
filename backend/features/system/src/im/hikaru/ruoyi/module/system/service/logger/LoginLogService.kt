package im.hikaru.ruoyi.module.system.service.logger

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.api.logger.dto.LoginLogCreateReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.LoginLogDO

interface LoginLogService {
    fun getLoginLog(id: Long): LoginLogDO?
    fun getLoginLogPage(req: LoginLogPageReqVO): PageResult<LoginLogDO>
    fun createLoginLog(req: LoginLogCreateReqDTO)
}
