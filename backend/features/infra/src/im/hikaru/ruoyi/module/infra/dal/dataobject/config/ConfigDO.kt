package im.hikaru.ruoyi.module.infra.dal.dataobject.config

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

/**
 * 参数配置 DO (迁移自 Java, 去 Lombok)
 * @author 芋道源码
 */
class ConfigDO : BaseEntity {
    var id: Long? = null
    var category: String? = null
    var type: Int? = null
    var name: String? = null
    var key: String? = null
    var value: String? = null
    var visible: Boolean? = null
    var remark: String? = null

    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
}
