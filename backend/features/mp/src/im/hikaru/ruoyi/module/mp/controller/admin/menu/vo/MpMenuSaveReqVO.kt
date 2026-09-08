package im.hikaru.ruoyi.module.mp.controller.admin.menu.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号菜单保存 Request VO")
class MpMenuSaveReqVO {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @field:NotNull(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
    @field:NotEmpty(message = "菜单不能为空")
    @Valid
    var menus: List<Menu>? = null

    @Schema(description = "管理后台 - 公众号菜单保存时的每个菜单")
    class Menu : MpMenuBaseVO() {
        var children: List<Menu>? = null
    }
}
