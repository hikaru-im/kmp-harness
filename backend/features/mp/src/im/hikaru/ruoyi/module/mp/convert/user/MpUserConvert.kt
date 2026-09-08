package im.hikaru.ruoyi.module.mp.convert.user

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserUpdateReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.user.MpUserDO
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import me.chanjar.weixin.mp.bean.result.WxMpUser
import java.time.Instant
import java.time.ZoneId

object MpUserConvert {
    fun convert(account: MpAccountDO?, wxUser: WxMpUser): MpUserDO = MpUserDO().apply {
        openid = wxUser.openId
        unionId = wxUser.unionId
        subscribeStatus = if (wxUser.subscribe) CommonStatusEnum.ENABLE.status else CommonStatusEnum.DISABLE.status
        subscribeTime = wxUser.subscribeTime.takeIf { it > 0 }?.let {
            Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDateTime().toKotlinLocalDateTime()
        }
        nickname = wxUser.nickname
        headImageUrl = wxUser.headImgUrl
        language = wxUser.language
        remark = wxUser.remark
        tagIds = wxUser.tagIds?.toList()
        accountId = account?.id
        appId = account?.appId
    }

    fun convert(bean: MpUserUpdateReqVO): MpUserDO = MpUserDO().apply {
        id = bean.id
        nickname = bean.nickname
        remark = bean.remark
        tagIds = bean.tagIds
    }

    fun convert(bean: MpUserDO): MpUserRespVO = MpUserRespVO().apply {
        id = bean.id
        openid = bean.openid
        unionId = bean.unionId
        subscribeStatus = bean.subscribeStatus
        subscribeTime = bean.subscribeTime?.toJavaLocalDateTime()
        unsubscribeTime = bean.unsubscribeTime?.toJavaLocalDateTime()
        nickname = bean.nickname
        headImageUrl = bean.headImageUrl
        language = bean.language
        country = bean.country
        province = bean.province
        city = bean.city
        remark = bean.remark
        tagIds = bean.tagIds
        accountId = bean.accountId
        appId = bean.appId
        createTime = bean.createTime?.toJavaLocalDateTime()
    }

    fun convertPage(page: PageResult<MpUserDO>) = PageResult(
        total = page.total,
        list = page.list.map(::convert),
    )
}
