package im.hikaru.ruoyi.module.infra.dal.dataobject.file

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class FileContentDO : BaseEntity {
    var id: Long? = null
    var configId: Long? = null
    var path: String? = null
    var content: ByteArray? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}
