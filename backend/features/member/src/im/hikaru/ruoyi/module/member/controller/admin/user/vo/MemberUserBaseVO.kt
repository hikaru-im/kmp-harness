package im.hikaru.ruoyi.module.member.controller.admin.user.vo

import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import org.hibernate.validator.constraints.URL
import org.springframework.format.annotation.DateTimeFormat

open class MemberUserBaseVO {
    @field:Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15601691300")
    @field:NotNull(message = "手机号不能为空")
    var mobile: String? = null
    @field:Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @field:NotNull(message = "状态不能为空")
    var status: Byte? = null
    @field:Schema(description = "邮箱", example = "member@iocoder.cn")
    @field:Email(message = "邮箱格式不正确")
    @field:Size(max = 50, message = "邮箱长度不能超过 50 个字符")
    var email: String? = null
    @field:Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @field:NotNull(message = "用户昵称不能为空")
    var nickname: String? = null
    @field:Schema(description = "头像", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/x.png")
    @URL(message = "头像必须是 URL 格式")
    var avatar: String? = null
    @field:Schema(description = "用户昵称", example = "李四")
    var name: String? = null
    @field:Schema(description = "用户性别", example = "1")
    var sex: Int? = null
    @field:Schema(description = "所在地编号", example = "4371")
    var areaId: Long? = null
    @field:Schema(description = "所在地全程", example = "上海上海市普陀区")
    var areaName: String? = null
    @field:Schema(description = "出生日期", example = "2023-03-12")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    var birthday: LocalDateTime? = null
    @field:Schema(description = "会员备注", example = "我是小备注")
    var mark: String? = null
    @field:Schema(description = "会员标签", example = "[1, 2]")
    var tagIds: List<Long>? = null
    @field:Schema(description = "会员等级编号", example = "1")
    var levelId: Long? = null
    @field:Schema(description = "用户分组编号", example = "1")
    var groupId: Long? = null
}
