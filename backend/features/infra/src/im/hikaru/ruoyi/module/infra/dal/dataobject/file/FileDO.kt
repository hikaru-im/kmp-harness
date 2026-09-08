package im.hikaru.ruoyi.module.infra.dal.dataobject.file

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

/**
 * 文件 DO
 */
class FileDO : BaseEntity {
    var id: Long? = null
    var configId: Long? = null
    var path: String? = null
    var name: String? = null
    var url: String? = null
    var type: String? = null
    var size: Long? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
}
