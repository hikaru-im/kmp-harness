package im.hikaru.ruoyi.module.mp.convert.material

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialUploadRespVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.material.MpMaterialDO
import kotlinx.datetime.toJavaLocalDateTime
import me.chanjar.weixin.mp.bean.material.WxMpMaterial
import java.io.File

object MpMaterialConvert {
    fun convert(
        mediaId: String,
        type: String,
        url: String,
        account: MpAccountDO,
        name: String?,
        title: String? = null,
        introduction: String? = null,
        mpUrl: String? = null,
    ): MpMaterialDO = MpMaterialDO().apply {
        this.mediaId = mediaId
        this.type = type
        this.url = url
        this.accountId = account.id
        this.appId = account.appId
        this.name = name
        this.title = title
        this.introduction = introduction
        this.mpUrl = mpUrl
    }

    fun convert(bean: MpMaterialDO): MpMaterialUploadRespVO = MpMaterialUploadRespVO().apply {
        mediaId = bean.mediaId
        url = bean.url
    }

    fun convertPage(page: PageResult<MpMaterialDO>) = PageResult(
        total = page.total,
        list = page.list.map { bean ->
            MpMaterialRespVO().apply {
                id = bean.id
                accountId = bean.accountId
                appId = bean.appId
                mediaId = bean.mediaId
                type = bean.type
                permanent = bean.permanent
                url = bean.url
                name = bean.name
                mpUrl = bean.mpUrl
                title = bean.title
                introduction = bean.introduction
                createTime = bean.createTime?.toJavaLocalDateTime()
            }
        },
    )

    fun convert(name: String, file: File, title: String?, introduction: String?): WxMpMaterial =
        WxMpMaterial(name, file, title, introduction)
}
