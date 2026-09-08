package im.hikaru.ruoyi.module.infra.dal.dataobject.codegen

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class CodegenColumnDO : BaseEntity {
    var id: Long? = null
    var tableId: Long? = null
    var columnName: String? = null
    var dataType: String? = null
    var columnComment: String? = null
    var nullable: Boolean? = null
    var primaryKey: Boolean? = null
    var ordinalPosition: Int? = null
    var javaType: String? = null
    var javaField: String? = null
    var dictType: String? = null
    var example: String? = null
    var createOperation: Boolean? = null
    var updateOperation: Boolean? = null
    var listOperation: Boolean? = null
    var listOperationCondition: String? = null
    var listOperationResult: Boolean? = null
    var htmlType: String? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}
