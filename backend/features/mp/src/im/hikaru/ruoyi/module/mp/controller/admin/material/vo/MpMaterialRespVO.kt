package im.hikaru.ruoyi.module.mp.controller.admin.material.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 公众号素材 Response VO")
class MpMaterialRespVO {
    @field:Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var accountId: Long? = null
    @field:Schema(description = "公众号账号的 appId", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx1234567890")
    var appId: String? = null
    @field:Schema(description = "素材的 media_id", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
    var mediaId: String? = null
    @field:Schema(description = "文件类型 参见 WxConsts.MediaFileType 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "image")
    var type: String? = null
    @field:Schema(description = "是否永久 true - 永久；false - 临时", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    var permanent: Boolean? = null
    @field:Schema(description = "素材的 URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/1.png")
    var url: String? = null
    @field:Schema(description = "名字", example = "yunai.png")
    var name: String? = null
    @field:Schema(description = "公众号文件 URL 只有【永久素材】使用", example = "https://mmbiz.qpic.cn/xxx.mp3")
    var mpUrl: String? = null
    @field:Schema(description = "视频素材的标题 只有【永久素材】使用", example = "我是标题")
    var title: String? = null
    @field:Schema(description = "视频素材的描述 只有【永久素材】使用", example = "我是介绍")
    var introduction: String? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
