package im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.column

import jakarta.validation.constraints.NotNull

open class CodegenColumnSaveReqVO {
    var id: Long? = null
    @field:NotNull var tableId: Long? = null
    @field:NotNull var columnName: String? = null
    @field:NotNull var dataType: String? = null
    @field:NotNull var columnComment: String? = null
    @field:NotNull var nullable: Boolean? = null
    @field:NotNull var primaryKey: Boolean? = null
    @field:NotNull var ordinalPosition: Int? = null
    @field:NotNull var javaType: String? = null
    @field:NotNull var javaField: String? = null
    var dictType: String? = null
    var example: String? = null
    @field:NotNull var createOperation: Boolean? = null
    @field:NotNull var updateOperation: Boolean? = null
    @field:NotNull var listOperation: Boolean? = null
    @field:NotNull var listOperationCondition: String? = null
    @field:NotNull var listOperationResult: Boolean? = null
    @field:NotNull var htmlType: String? = null
}
