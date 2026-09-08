package im.hikaru.ruoyi.module.infra.dal.dataobject.logger

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

/**
 * API 访问日志 DO (迁移自 Java, 去 Lombok)
 * @author 芋道源码
 */
class ApiAccessLogDO : BaseEntity {
    var id: Long? = null
    var traceId: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var applicationName: String? = null
    var requestMethod: String? = null
    var requestUrl: String? = null
    var requestParams: String? = null
    var responseBody: String? = null
    var userIp: String? = null
    var userAgent: String? = null
    var operateModule: String? = null
    var operateName: String? = null
    var operateType: Int? = null
    var beginTime: LocalDateTime? = null
    var endTime: LocalDateTime? = null
    var duration: Int? = null
    var resultCode: Int? = null
    var resultMsg: String? = null

    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
}
