package im.hikaru.ruoyi.module.infra.dal.dataobject.file

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
import kotlinx.datetime.LocalDateTime

class FileConfigDO : BaseEntity {
    var id: Long? = null
    var name: String? = null
    var storage: Int? = null
    var config: FileClientConfig? = null
    var master: Boolean? = null
    var remark: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
}
