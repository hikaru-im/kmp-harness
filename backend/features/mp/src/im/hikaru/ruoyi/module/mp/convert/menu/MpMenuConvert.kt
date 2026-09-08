package im.hikaru.ruoyi.module.mp.convert.menu

import im.hikaru.ruoyi.module.mp.controller.admin.menu.vo.MpMenuBaseVO
import im.hikaru.ruoyi.module.mp.controller.admin.menu.vo.MpMenuRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.menu.vo.MpMenuSaveReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.menu.MpMenuDO
import im.hikaru.ruoyi.module.mp.service.message.bo.MpMessageSendOutReqBO
import kotlinx.datetime.toJavaLocalDateTime
import me.chanjar.weixin.common.bean.menu.WxMenuButton

object MpMenuConvert {
    fun convert(bean: MpMenuDO): MpMenuRespVO = copyBase(bean, MpMenuRespVO()).apply {
        id = bean.id
        accountId = bean.accountId
        appId = bean.appId
        createTime = bean.createTime?.toJavaLocalDateTime()
    }

    fun convertList(list: List<MpMenuDO>): List<MpMenuRespVO> = list.map(::convert)

    fun convert(openid: String, menu: MpMenuDO): MpMessageSendOutReqBO = MpMessageSendOutReqBO().apply {
        appId = menu.appId
        this.openid = openid
        type = menu.replyMessageType
        content = menu.replyContent
        mediaId = menu.replyMediaId
        thumbMediaId = menu.replyThumbMediaId
        title = menu.replyTitle
        description = menu.replyDescription
        articles = menu.replyArticles
        musicUrl = menu.replyMusicUrl
        hqMusicUrl = menu.replyHqMusicUrl
    }

    fun convert(list: List<MpMenuSaveReqVO.Menu>): List<WxMenuButton> = list.map(::convert)

    fun convert(bean: MpMenuSaveReqVO.Menu): WxMenuButton = WxMenuButton().apply {
        name = bean.name
        key = bean.menuKey
        type = bean.type
        url = bean.url
        appId = bean.miniProgramAppId
        pagePath = bean.miniProgramPagePath
        mediaId = bean.articleId
        subButtons = bean.children.orEmpty().map(::convert)
    }

    fun convertToEntity(menu: MpMenuSaveReqVO.Menu): MpMenuDO = MpMenuDO().apply {
        name = menu.name
        menuKey = menu.menuKey
        type = menu.type
        url = menu.url
        miniProgramAppId = menu.miniProgramAppId
        miniProgramPagePath = menu.miniProgramPagePath
        articleId = menu.articleId
        replyMessageType = menu.replyMessageType
        replyContent = menu.replyContent
        replyMediaId = menu.replyMediaId
        replyMediaUrl = menu.replyMediaUrl
        replyTitle = menu.replyTitle
        replyDescription = menu.replyDescription
        replyThumbMediaId = menu.replyThumbMediaId
        replyThumbMediaUrl = menu.replyThumbMediaUrl
        replyArticles = menu.replyArticles
        replyMusicUrl = menu.replyMusicUrl
        replyHqMusicUrl = menu.replyHqMusicUrl
    }

    private fun <T : MpMenuBaseVO> copyBase(source: MpMenuDO, target: T) = target.apply {
        name = source.name
        menuKey = source.menuKey
        parentId = source.parentId
        type = source.type
        url = source.url
        miniProgramAppId = source.miniProgramAppId
        miniProgramPagePath = source.miniProgramPagePath
        articleId = source.articleId
        replyMessageType = source.replyMessageType
        replyContent = source.replyContent
        replyMediaId = source.replyMediaId
        replyMediaUrl = source.replyMediaUrl
        replyTitle = source.replyTitle
        replyDescription = source.replyDescription
        replyThumbMediaId = source.replyThumbMediaId
        replyThumbMediaUrl = source.replyThumbMediaUrl
        replyArticles = source.replyArticles
        replyMusicUrl = source.replyMusicUrl
        replyHqMusicUrl = source.replyHqMusicUrl
    }
}
