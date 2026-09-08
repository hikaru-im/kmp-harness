package im.hikaru.ruoyi.module.infra.dal.dataobject.codegen

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class CodegenTableDO : BaseEntity {
    var id: Long? = null
    var dataSourceConfigId: Long? = null
    var scene: Int? = null
    var tableName: String? = null
    var tableComment: String? = null
    var remark: String? = null
    var moduleName: String? = null
    var businessName: String? = null
    var className: String? = null
    var classComment: String? = null
    var author: String? = null
    var templateType: Int? = null
    var frontType: Int? = null
    var parentMenuId: Long? = null
    var masterTableId: Long? = null
    var subJoinColumnId: Long? = null
    var subJoinMany: Boolean? = null
    var treeParentColumnId: Long? = null
    var treeNameColumnId: Long? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}
