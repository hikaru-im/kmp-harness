package im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.validation.InEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "Admin dictionary data page request")
class DictDataPageReqVO : PageParam() {
    @field:Size(max = 100)
    var label: String? = null

    @field:Size(max = 100)
    var dictType: String? = null

    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null
}
