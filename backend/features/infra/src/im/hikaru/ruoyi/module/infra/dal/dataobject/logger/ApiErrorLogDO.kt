package im.hikaru.ruoyi.module.infra.dal.dataobject.logger

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

/**
 * API 错误日志 DO (迁移自 Java, 去 Lombok)
 * @author 芋道源码
 */
class ApiErrorLogDO : BaseEntity {
    var id: Long? = null
    var traceId: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var applicationName: String? = null
    var requestMethod: String? = null
    var requestUrl: String? = null
    var requestParams: String? = null
    var userIp: String? = null
    var userAgent: String? = null
    var exceptionTime: LocalDateTime? = null
    var exceptionName: String? = null
    var exceptionMessage: String? = null
    var exceptionRootCauseMessage: String? = null
    var exceptionStackTrace: String? = null
    var exceptionClassName: String? = null
    var exceptionFileName: String? = null
    var exceptionMethodName: String? = null
    var exceptionLineNumber: Int? = null
    var processStatus: Int? = null
    var processTime: LocalDateTime? = null
    var processUserId: Long? = null

    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
}
