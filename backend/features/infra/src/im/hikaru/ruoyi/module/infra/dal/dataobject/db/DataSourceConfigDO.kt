package im.hikaru.ruoyi.module.infra.dal.dataobject.db

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

/**
 * 数据源配置 DO (迁移自 Java, 去 Lombok)
 * @author 芋道源码
 */
class DataSourceConfigDO : BaseEntity {

    /** 主键编号 - Master 数据源 */
    var id: Long? = null
    /** 连接名 */
    var name: String? = null
    /** 数据源连接 */
    var url: String? = null
    /** 用户名 */
    var username: String? = null
    /** 密码 */
    var password: String? = null

    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false

    companion object {
        /** 主键编号 - Master 数据源 */
        const val ID_MASTER: Long = 0L
    }
}
