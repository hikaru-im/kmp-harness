package im.hikaru.ruoyi.module.system.controller.admin.logger.vo.operatelog

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import java.time.LocalDateTime

class OperateLogPageReqVO : PageParam() {
    var userId: Long? = null
    var bizId: Long? = null
    var type: String? = null
    var subType: String? = null
    var action: String? = null
    var createTime: List<KotlinLocalDateTime>? = null
}

@ExcelIgnoreUnannotated
class OperateLogRespVO {
    @ExcelProperty("Log id")
    var id: Long? = null
    var traceId: String? = null
    var userId: Long? = null
    @ExcelProperty("Operator")
    var userName: String? = null
    @ExcelProperty("User type")
    var userType: Int? = null
    @ExcelProperty("Operation module")
    var type: String? = null
    @ExcelProperty("Operation")
    var subType: String? = null
    @ExcelProperty("Business id")
    var bizId: Long? = null
    var action: String? = null
    var extra: String? = null
    var requestMethod: String? = null
    var requestUrl: String? = null
    var userIp: String? = null
    var userAgent: String? = null
    @ExcelProperty("Created at")
    var createTime: LocalDateTime? = null
}
