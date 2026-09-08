package im.hikaru.ruoyi.module.system.controller.admin.permission.vo.menu

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class MenuSaveVO {
    var id: Long? = null; @field:NotBlank var name: String? = null; var permission: String? = null; @field:NotNull var type: Int? = null; @field:NotNull var sort: Int? = null
    var parentId: Long? = null; var path: String? = null; var icon: String? = null; var component: String? = null; var componentName: String? = null
    @field:NotNull var status: Int? = null; var visible: Boolean? = null; var keepAlive: Boolean? = null; var alwaysShow: Boolean? = null
}
