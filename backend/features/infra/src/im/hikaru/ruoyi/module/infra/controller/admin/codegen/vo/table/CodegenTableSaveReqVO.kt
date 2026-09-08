package im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table

import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenSceneEnum
import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenTemplateTypeEnum
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull

open class CodegenTableSaveReqVO {
    var id: Long? = null
    @field:NotNull var scene: Int? = null
    @field:NotNull var tableName: String? = null
    @field:NotNull var tableComment: String? = null
    var remark: String? = null
    @field:NotNull var moduleName: String? = null
    @field:NotNull var businessName: String? = null
    @field:NotNull var className: String? = null
    @field:NotNull var classComment: String? = null
    @field:NotNull var author: String? = null
    @field:NotNull var templateType: Int? = null
    @field:NotNull var frontType: Int? = null
    var parentMenuId: Long? = null
    var masterTableId: Long? = null
    var subJoinColumnId: Long? = null
    var subJoinMany: Boolean? = null
    var treeParentColumnId: Long? = null
    var treeNameColumnId: Long? = null

    @get:AssertTrue(message = "Parent menu is required for admin generation")
    @get:JsonIgnore
    val parentMenuIdValid: Boolean
        get() = scene != CodegenSceneEnum.ADMIN.scene || parentMenuId != null

    @get:AssertTrue(message = "Sub-table relation is incomplete")
    @get:JsonIgnore
    val subValid: Boolean
        get() = templateType != CodegenTemplateTypeEnum.SUB.type ||
            (masterTableId != null && subJoinColumnId != null && subJoinMany != null)

    @get:AssertTrue(message = "Tree-table relation is incomplete")
    @get:JsonIgnore
    val treeValid: Boolean
        get() = templateType != CodegenTemplateTypeEnum.TREE.type ||
            (treeParentColumnId != null && treeNameColumnId != null)
}
