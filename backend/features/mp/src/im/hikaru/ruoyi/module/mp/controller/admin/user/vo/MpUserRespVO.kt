package im.hikaru.ruoyi.module.mp.controller.admin.user.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 公众号粉丝 Response VO")
class MpUserRespVO {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "公众号粉丝标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "o6_bmjrPTlm6_2sgVt7hMZOPfL2M")
    var openid: String? = null
    @field:Schema(description = "微信生态唯一标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "o6_bmjrPTlm6_2sgVt7hMZOPfL2M")
    var unionId: String? = null
    @field:Schema(description = "关注状态 参见 CommonStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var subscribeStatus: Int? = null
    @field:Schema(description = "关注时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var subscribeTime: LocalDateTime? = null
    @field:Schema(description = "取消关注时间")
    var unsubscribeTime: LocalDateTime? = null
    @field:Schema(description = "昵称", example = "芋道")
    var nickname: String? = null
    @field:Schema(description = "头像地址", example = "https://www.iocoder.cn/1.png")
    var headImageUrl: String? = null
    @field:Schema(description = "语言", example = "zh_CN")
    var language: String? = null
    @field:Schema(description = "国家", example = "中国")
    var country: String? = null
    @field:Schema(description = "省份", example = "广东省")
    var province: String? = null
    @field:Schema(description = "城市", example = "广州市")
    var city: String? = null
    @field:Schema(description = "备注", example = "你是一个芋头嘛")
    var remark: String? = null
    @field:Schema(description = "标签编号数组", example = "1,2,3")
    var tagIds: List<Long>? = null
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var accountId: Long? = null
    @field:Schema(description = "公众号账号的 appId", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx1234567890")
    var appId: String? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
