package im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "Admin dictionary data create/update request")
class DictDataSaveReqVO {
    var id: Long? = null

    @field:NotNull
    var sort: Int? = null

    @field:NotBlank
    @field:Size(max = 100)
    var label: String? = null

    @field:NotBlank
    @field:Size(max = 100)
    var value: String? = null

    @field:NotBlank
    @field:Size(max = 100)
    var dictType: String? = null

    @field:NotNull
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null
    var colorType: String? = null
    var cssClass: String? = null
    var remark: String? = null
}
