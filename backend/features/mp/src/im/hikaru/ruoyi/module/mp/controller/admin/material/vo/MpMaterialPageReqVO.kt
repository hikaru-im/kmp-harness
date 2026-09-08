package im.hikaru.ruoyi.module.mp.controller.admin.material.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号素材的分页 Request VO")
class MpMaterialPageReqVO : PageParam() {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @field:NotNull(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
    @field:Schema(description = "是否永久", example = "true")
    var permanent: Boolean? = null
    @field:Schema(description = "文件类型 参见 WxConsts.MediaFileType 枚举", example = "image")
    var type: String? = null
}
