package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message

import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import im.hikaru.ruoyi.module.mp.framework.mp.core.util.*
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号消息发送 Request VO")
class MpMessageSendReqVO {
    @field:Schema(description = "公众号粉丝的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "公众号粉丝的编号不能为空")
    var userId: Long? = null
    @field:Schema(description = "消息类型 TEXT/IMAGE/VOICE/VIDEO/NEWS", requiredMode = Schema.RequiredMode.REQUIRED, example = "text")
    @field:NotEmpty(message = "消息类型不能为空")
    var type: String? = null
    @field:Schema(description = "消息内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好呀")
    @field:NotEmpty(message = "消息内容不能为空", groups = [TextMessageGroup::class])
    var content: String? = null
    @field:Schema(description = "媒体 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "qqc_2Fot30Jse-HDoZmo5RrUDijz2nGUkP")
    @field:NotEmpty(message = "消息内容不能为空", groups = [ImageMessageGroup::class, VoiceMessageGroup::class, VideoMessageGroup::class])
    var mediaId: String? = null
    @field:Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "没有标题")
    @field:NotEmpty(message = "消息内容不能为空", groups = [VideoMessageGroup::class])
    var title: String? = null
    @field:Schema(description = "描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "你猜")
    @field:NotEmpty(message = "消息描述不能为空", groups = [VideoMessageGroup::class])
    var description: String? = null
    @field:Schema(description = "缩略图的媒体 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "qqc_2Fot30Jse-HDoZmo5RrUDijz2nGUkP")
    @field:NotEmpty(message = "缩略图的媒体 id 不能为空", groups = [MusicMessageGroup::class])
    var thumbMediaId: String? = null
    @field:Schema(description = "图文消息", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @field:NotNull(message = "图文消息不能为空", groups = [NewsMessageGroup::class])
    var articles: List<MpMessageDO.Article>? = null
    @field:Schema(description = "音乐链接 消息类型为 MUSIC 时", example = "https://www.iocoder.cn/music.mp3")
    var musicUrl: String? = null
    @field:Schema(description = "高质量音乐链接 消息类型为 MUSIC 时", example = "https://www.iocoder.cn/music.mp3")
    var hqMusicUrl: String? = null
}
