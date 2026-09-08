package im.hikaru.ruoyi.module.member.controller.admin.address.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 用户收件地址 Response VO")
class AddressRespVO : AddressBaseVO() {
    @field:Schema(description = "收件地址编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "7380")
    var id: Long? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
    @field:Schema(description = "地区名称", example = "上海上海市普陀区")
    var areaName: String? = null
}
