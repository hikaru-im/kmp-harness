package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message

import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import me.chanjar.weixin.common.api.WxConsts

@Schema(description = "管理后台 - 公众号消息 Response VO")
class MpMessageRespVO {
    @field:Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Int? = null
    @field:Schema(description = "微信公众号消息 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "23953173569869169")
    var msgId: Long? = null
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var accountId: Long? = null
    @field:Schema(description = "公众号账号的 appid", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx1234567890")
    var appId: String? = null
    @field:Schema(description = "公众号粉丝编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    var userId: Long? = null
    @field:Schema(description = "公众号粉丝标志", requiredMode = Schema.RequiredMode.REQUIRED, example = "o6_bmjrPTlm6_2sgVt7hMZOPfL2M")
    var openid: String? = null
    @field:Schema(description = "消息类型 参见 WxConsts.XmlMsgType 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "text")
    var type: String? = null
    @field:Schema(description = "消息来源 参见 MpMessageSendFromEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var sendFrom: Int? = null
    @field:Schema(description = "消息内容 消息类型为 text 时，才有值", example = "你好呀")
    var content: String? = null
    @field:Schema(description = "媒体素材的编号 消息类型为 image、voice、video 时，才有值", example = "1234567890")
    var mediaId: String? = null
    @field:Schema(description = "媒体文件的 URL 消息类型为 image、voice、video 时，才有值", example = "https://www.iocoder.cn/xxx.png")
    var mediaUrl: String? = null
    @field:Schema(description = "语音识别后文本 消息类型为 voice 时，才有值", example = "语音识别后文本")
    var recognition: String? = null
    @field:Schema(description = "语音格式 消息类型为 voice 时，才有值", example = "amr")
    var format: String? = null
    @field:Schema(description = "标题 消息类型为 video、music、link 时，才有值", example = "我是标题")
    var title: String? = null
    @field:Schema(description = "描述 消息类型为 video、music 时，才有值", example = "我是描述")
    var description: String? = null
    @field:Schema(description = "缩略图的媒体 id 消息类型为 video、music 时，才有值", example = "1234567890")
    var thumbMediaId: String? = null
    @field:Schema(description = "缩略图的媒体 URL 消息类型为 video、music 时，才有值", example = "https://www.iocoder.cn/xxx.png")
    var thumbMediaUrl: String? = null
    @field:Schema(description = "点击图文消息跳转链接 消息类型为 link 时，才有值", example = "https://www.iocoder.cn")
    var url: String? = null
    @field:Schema(description = "地理位置维度 消息类型为 location 时，才有值", example = "23.137466")
    var locationX: Double? = null
    @field:Schema(description = "地理位置经度 消息类型为 location 时，才有值", example = "113.352425")
    var locationY: Double? = null
    @field:Schema(description = "地图缩放大小 消息类型为 location 时，才有值", example = "13")
    var scale: Double? = null
    @field:Schema(description = "详细地址 消息类型为 location 时，才有值", example = "杨浦区黄兴路 221-4 号临")
    var label: String? = null
    var articles: List<MpMessageDO.Article>? = null
    @field:Schema(description = "音乐链接 消息类型为 music 时，才有值", example = "https://www.iocoder.cn/xxx.mp3")
    var musicUrl: String? = null
    @field:Schema(description = "高质量音乐链接 消息类型为 music 时，才有值", example = "https://www.iocoder.cn/xxx.mp3")
    var hqMusicUrl: String? = null
    @field:Schema(description = "事件类型 参见 WxConsts.EventType 枚举", example = "subscribe")
    var event: String? = null
    @field:Schema(description = "事件 Key 参见 WxConsts.EventType 枚举", example = "qrscene_123456")
    var eventKey: String? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
