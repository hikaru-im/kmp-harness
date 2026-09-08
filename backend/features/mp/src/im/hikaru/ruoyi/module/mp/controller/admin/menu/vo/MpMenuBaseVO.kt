package im.hikaru.ruoyi.module.mp.controller.admin.menu.vo

import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import im.hikaru.ruoyi.module.mp.framework.mp.core.util.*
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import me.chanjar.weixin.common.api.WxConsts
import org.hibernate.validator.constraints.URL

open class MpMenuBaseVO {
    var name: String? = null
    var menuKey: String? = null
    var parentId: Long? = null
    var type: String? = null
    @field:Schema(description = "网页链接", example = "https://www.iocoder.cn/")
    @field:NotEmpty(message = "网页链接不能为空", groups = [ViewButtonGroup::class, MiniProgramButtonGroup::class])
    @URL(message = "网页链接必须是 URL 格式")
    var url: String? = null
    @field:Schema(description = "小程序的 appId", example = "wx1234567890")
    @field:NotEmpty(message = "小程序的 appId 不能为空", groups = [MiniProgramButtonGroup::class])
    var miniProgramAppId: String? = null
    @field:Schema(description = "小程序的页面路径", example = "pages/index/index")
    @field:NotEmpty(message = "小程序的页面路径不能为空", groups = [MiniProgramButtonGroup::class])
    var miniProgramPagePath: String? = null
    @field:Schema(description = "跳转图文的媒体编号", example = "jCQk93AIIgp8ixClWcW_NXXqBKInNWNmq2XnPeDZl7IMVqWiNeL4FfELtggRXd83")
    @field:NotEmpty(message = "跳转图文的媒体编号不能为空", groups = [ViewLimitedButtonGroup::class])
    var articleId: String? = null
    @field:Schema(description = "回复的消息类型 枚举 TEXT、IMAGE、VOICE、VIDEO、NEWS、MUSIC", example = "text")
    @field:NotEmpty(message = "回复的消息类型不能为空", groups = [ClickButtonGroup::class, ScanCodeWaitMsgButtonGroup::class])
    var replyMessageType: String? = null
    @field:Schema(description = "回复的消息内容", example = "欢迎关注")
    @field:NotEmpty(message = "回复的消息内容不能为空", groups = [TextMessageGroup::class])
    var replyContent: String? = null
    @field:Schema(description = "回复的媒体 id", example = "123456")
    @field:NotEmpty(message = "回复的消息 mediaId 不能为空", groups = [ImageMessageGroup::class, VoiceMessageGroup::class, VideoMessageGroup::class])
    var replyMediaId: String? = null
    @field:Schema(description = "回复的媒体 URL", example = "https://www.iocoder.cn/xxx.jpg")
    @field:NotEmpty(message = "回复的消息 mediaId 不能为空", groups = [ImageMessageGroup::class, VoiceMessageGroup::class, VideoMessageGroup::class])
    var replyMediaUrl: String? = null
    @field:Schema(description = "缩略图的媒体 id", example = "123456")
    @field:NotEmpty(message = "回复的消息 thumbMediaId 不能为空", groups = [MusicMessageGroup::class])
    var replyThumbMediaId: String? = null
    @field:Schema(description = "缩略图的媒体 URL", example = "https://www.iocoder.cn/xxx.jpg")
    @field:NotEmpty(message = "回复的消息 thumbMedia 地址不能为空", groups = [MusicMessageGroup::class])
    var replyThumbMediaUrl: String? = null
    @field:Schema(description = "回复的标题", example = "视频标题")
    @field:NotEmpty(message = "回复的消息标题不能为空", groups = [VideoMessageGroup::class])
    var replyTitle: String? = null
    @field:Schema(description = "回复的描述", example = "视频描述")
    @field:NotEmpty(message = "消息描述不能为空", groups = [VideoMessageGroup::class])
    var replyDescription: String? = null
    @field:NotNull(message = "回复的图文消息不能为空", groups = [NewsMessageGroup::class, ViewLimitedButtonGroup::class])
    @Valid
    var replyArticles: List<MpMessageDO.Article>? = null
    @field:Schema(description = "回复的音乐链接", example = "https://www.iocoder.cn/xxx.mp3")
    @field:NotEmpty(message = "回复的音乐链接不能为空", groups = [MusicMessageGroup::class])
    @URL(message = "回复的高质量音乐链接格式不正确", groups = [MusicMessageGroup::class])
    var replyMusicUrl: String? = null
    @field:Schema(description = "高质量音乐链接", example = "https://www.iocoder.cn/xxx.mp3")
    @field:NotEmpty(message = "回复的高质量音乐链接不能为空", groups = [MusicMessageGroup::class])
    @URL(message = "回复的高质量音乐链接格式不正确", groups = [MusicMessageGroup::class])
    var replyHqMusicUrl: String? = null
}
