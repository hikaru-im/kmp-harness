package im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class DictTypeSaveReqVO {
    var id: Long? = null

    @field:NotBlank
    @field:Size(max = 100)
    var name: String? = null

    @field:NotNull
    @field:Size(max = 100)
    var type: String? = null

    @field:NotNull
    var status: Int? = null
    var remark: String? = null
}
