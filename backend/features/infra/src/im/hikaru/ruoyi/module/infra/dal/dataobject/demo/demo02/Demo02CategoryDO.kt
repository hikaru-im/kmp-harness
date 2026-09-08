package im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo02

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class Demo02CategoryDO : BaseEntity {
    var id: Long? = null
    var name: String? = null
    var parentId: Long? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false

    companion object {
        const val PARENT_ID_ROOT: Long = 0L
    }
}
