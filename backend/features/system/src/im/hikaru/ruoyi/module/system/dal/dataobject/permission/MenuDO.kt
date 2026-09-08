package im.hikaru.ruoyi.module.system.dal.dataobject.permission

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class MenuDO : BaseEntity {
    companion object { const val ID_ROOT = 0L }
    var id: Long? = null; var name: String? = null; var permission: String? = null; var type: Int? = null; var sort: Int? = null; var parentId: Long? = null
    var path: String? = null; var icon: String? = null; var component: String? = null; var componentName: String? = null; var status: Int? = null
    var visible: Boolean? = null; var keepAlive: Boolean? = null; var alwaysShow: Boolean? = null
    override var createTime: LocalDateTime? = null; override var updateTime: LocalDateTime? = null; override var creator: String? = null; override var updater: String? = null
    override var deleted: Boolean = false
}
