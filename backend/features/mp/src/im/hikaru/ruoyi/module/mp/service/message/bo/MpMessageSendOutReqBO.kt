package im.hikaru.ruoyi.module.mp.service.message.bo

import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import im.hikaru.ruoyi.module.mp.framework.mp.core.util.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import me.chanjar.weixin.common.api.WxConsts
import org.hibernate.validator.constraints.URL

class MpMessageSendOutReqBO {
    @field:NotEmpty(message = "公众号 appId 不能为空")
    var appId: String? = null
    @field:NotEmpty(message = "公众号粉丝 openid 不能为空")
    var openid: String? = null
    @field:NotEmpty(message = "消息类型不能为空")
    var type: String? = null
    @field:NotEmpty(message = "消息内容不能为空", groups = [TextMessageGroup::class])
    var content: String? = null
    @field:NotEmpty(message = "消息 mediaId 不能为空", groups = [ImageMessageGroup::class, VoiceMessageGroup::class, VideoMessageGroup::class])
    var mediaId: String? = null
    @field:NotEmpty(message = "消息 thumbMediaId 不能为空", groups = [MusicMessageGroup::class])
    var thumbMediaId: String? = null
    @field:NotEmpty(message = "消息标题不能为空", groups = [VideoMessageGroup::class])
    var title: String? = null
    @field:NotEmpty(message = "消息描述不能为空", groups = [VideoMessageGroup::class])
    var description: String? = null
    @Valid
    @field:NotNull(message = "图文消息不能为空", groups = [NewsMessageGroup::class])
    var articles: List<MpMessageDO.Article>? = null
    @field:NotEmpty(message = "音乐链接不能为空", groups = [MusicMessageGroup::class])
    @URL(message = "高质量音乐链接格式不正确", groups = [MusicMessageGroup::class])
    var musicUrl: String? = null
    @field:NotEmpty(message = "高质量音乐链接不能为空", groups = [MusicMessageGroup::class])
    @URL(message = "高质量音乐链接格式不正确", groups = [MusicMessageGroup::class])
    var hqMusicUrl: String? = null
}
