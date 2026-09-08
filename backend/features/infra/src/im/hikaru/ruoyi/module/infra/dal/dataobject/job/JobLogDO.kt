package im.hikaru.ruoyi.module.infra.dal.dataobject.job

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

/**
 * 定时任务的执行日志 DO (迁移自 Java, 去 Lombok)
 * @author 芋道源码
 */
class JobLogDO : BaseEntity {
    /** 日志编号 */
    var id: Long? = null
    /** 任务编号 */
    var jobId: Long? = null
    /** 处理器的名字 */
    var handlerName: String? = null
    /** 处理器的参数 */
    var handlerParam: String? = null
    /** 第几次执行。用于区分是不是重试执行。如果是重试执行，则 index 大于 1 */
    var executeIndex: Int? = null

    /** 开始执行时间 */
    var beginTime: LocalDateTime? = null
    /** 结束执行时间 */
    var endTime: LocalDateTime? = null
    /** 执行时长，单位：毫秒 */
    var duration: Int? = null
    /** 状态，参见 JobLogStatusEnum 枚举 */
    var status: Int? = null
    /** 结果数据 */
    var result: String? = null

    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
}
