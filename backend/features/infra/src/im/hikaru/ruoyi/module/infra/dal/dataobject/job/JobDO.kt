package im.hikaru.ruoyi.module.infra.dal.dataobject.job

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

/**
 * 定时任务 DO (迁移自 Java, 去 Lombok)
 * @author 芋道源码
 */
class JobDO : BaseEntity {
    /** 任务编号 */
    var id: Long? = null
    /** 任务名称 */
    var name: String? = null
    /** 任务状态，参见 JobStatusEnum 枚举 */
    var status: Int? = null
    /** 处理器的名字 */
    var handlerName: String? = null
    /** 处理器的参数 */
    var handlerParam: String? = null
    /** CRON 表达式 */
    var cronExpression: String? = null

    // ========== 重试相关字段 ==========
    /** 重试次数。如果不重试，则设置为 0 */
    var retryCount: Int? = null
    /** 重试间隔，单位：毫秒。如果没有间隔，则设置为 0 */
    var retryInterval: Int? = null

    // ========== 监控相关字段 ==========
    /** 监控超时时间，单位：毫秒。为空时，表示不监控 */
    var monitorTimeout: Int? = null

    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
}
