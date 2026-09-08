package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply

import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import im.hikaru.ruoyi.module.mp.enums.message.MpAutoReplyTypeEnum
import im.hikaru.ruoyi.module.mp.framework.mp.core.util.*
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import me.chanjar.weixin.common.api.WxConsts
import org.hibernate.validator.constraints.URL

open class MpAutoReplyBaseVO {
    @field:Schema(description = "回复类型 参见 MpAutoReplyTypeEnum 枚举", example = "1")
    @field:NotNull(message = "回复类型不能为空")
    var type: Int? = null
    @field:Schema(description = "请求的关键字 当 type 为 MpAutoReplyTypeEnum#KEYWORD 时，必填", example = "关键字")
    var requestKeyword: String? = null
    @field:Schema(description = "请求的匹配方式 当 type 为 MpAutoReplyTypeEnum#KEYWORD 时，必填", example = "1")
    var requestMatch: Int? = null
    @field:Schema(description = "请求的消息类型 当 type 为 MpAutoReplyTypeEnum#MESSAGE 时，必填", example = "text")
    var requestMessageType: String? = null
    @field:Schema(description = "回复的消息类型 枚举 TEXT、IMAGE、VOICE、VIDEO、NEWS、MUSIC", example = "text")
    @field:NotEmpty(message = "回复的消息类型不能为空")
    var responseMessageType: String? = null
    @field:Schema(description = "回复的消息内容", example = "欢迎关注")
    @field:NotEmpty(message = "回复的消息内容不能为空", groups = [TextMessageGroup::class])
    var responseContent: String? = null
    @field:Schema(description = "回复的媒体 id", example = "123456")
    @field:NotEmpty(message = "回复的消息 mediaId 不能为空", groups = [ImageMessageGroup::class, VoiceMessageGroup::class, VideoMessageGroup::class])
    var responseMediaId: String? = null
    @field:Schema(description = "回复的媒体 URL", example = "https://www.iocoder.cn/xxx.jpg")
    @field:NotEmpty(message = "回复的消息 mediaId 不能为空", groups = [ImageMessageGroup::class, VoiceMessageGroup::class, VideoMessageGroup::class])
    var responseMediaUrl: String? = null
    @field:Schema(description = "缩略图的媒体 id", example = "123456")
    @field:NotEmpty(message = "回复的消息 thumbMediaId 不能为空", groups = [MusicMessageGroup::class])
    var responseThumbMediaId: String? = null
    @field:Schema(description = "缩略图的媒体 URL", example = "https://www.iocoder.cn/xxx.jpg")
    @field:NotEmpty(message = "回复的消息 thumbMedia 地址不能为空", groups = [MusicMessageGroup::class])
    var responseThumbMediaUrl: String? = null
    @field:Schema(description = "回复的标题", example = "视频标题")
    @field:NotEmpty(message = "回复的消息标题不能为空", groups = [VideoMessageGroup::class])
    var responseTitle: String? = null
    @field:Schema(description = "回复的描述", example = "视频描述")
    @field:NotEmpty(message = "消息描述不能为空", groups = [VideoMessageGroup::class])
    var responseDescription: String? = null
    @field:NotNull(message = "回复的图文消息不能为空", groups = [NewsMessageGroup::class, ViewLimitedButtonGroup::class])
    @Valid
    var responseArticles: List<MpMessageDO.Article>? = null
    @field:Schema(description = "回复的音乐链接", example = "https://www.iocoder.cn/xxx.mp3")
    @field:NotEmpty(message = "回复的音乐链接不能为空", groups = [MusicMessageGroup::class])
    @URL(message = "回复的高质量音乐链接格式不正确", groups = [MusicMessageGroup::class])
    var responseMusicUrl: String? = null
    @field:Schema(description = "高质量音乐链接", example = "https://www.iocoder.cn/xxx.mp3")
    @field:NotEmpty(message = "回复的高质量音乐链接不能为空", groups = [MusicMessageGroup::class])
    @URL(message = "回复的高质量音乐链接格式不正确", groups = [MusicMessageGroup::class])
    var responseHqMusicUrl: String? = null
}
