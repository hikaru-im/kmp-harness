package im.hikaru.ruoyi.module.system.controller.admin.notice.vo

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.notice.NoticeTypeEnum
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class NoticeSaveReqVO {
    var id: Long? = null

    @field:NotBlank(message = "Title must not be blank")
    @field:Size(max = 50, message = "Title must not exceed 50 characters")
    var title: String? = null

    @field:NotNull(message = "Notice type must not be null")
    @field:InEnum(NoticeTypeEnum::class)
    var type: Int? = null

    @field:NotBlank(message = "Content must not be blank")
    var content: String? = null

    @field:NotNull(message = "Status must not be null")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null
}
