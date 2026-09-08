package im.hikaru.ruoyi.module.member.controller.app.address.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 用户收件地址 Response VO")
class AppAddressRespVO : AppAddressBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "地区名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "上海上海市普陀区")
    var areaName: String? = null
    @field:Schema(description = "Business version used by offline synchronization", requiredMode = Schema.RequiredMode.REQUIRED)
    var version: Long = 1
}
