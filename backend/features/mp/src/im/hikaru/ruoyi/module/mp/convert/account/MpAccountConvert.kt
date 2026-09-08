package im.hikaru.ruoyi.module.mp.convert.account

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountBaseVO
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountSimpleRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountUpdateReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import kotlinx.datetime.toJavaLocalDateTime

object MpAccountConvert {
    fun convert(bean: MpAccountCreateReqVO): MpAccountDO = copyBase(bean, MpAccountDO())
    fun convert(bean: MpAccountUpdateReqVO): MpAccountDO = copyBase(bean, MpAccountDO()).apply { id = bean.id }

    fun convert(bean: MpAccountDO): MpAccountRespVO = MpAccountRespVO().apply {
        id = bean.id
        name = bean.name
        account = bean.account
        appId = bean.appId
        url = bean.url
        appSecret = bean.appSecret
        token = bean.token
        aesKey = bean.aesKey
        remark = bean.remark
        qrCodeUrl = bean.qrCodeUrl
        createTime = bean.createTime?.toJavaLocalDateTime()
    }

    fun convertPage(page: PageResult<MpAccountDO>) = PageResult(
        total = page.total,
        list = page.list.map(::convert),
    )

    fun convertSimpleList(list: List<MpAccountDO>): List<MpAccountSimpleRespVO> = list.map { bean ->
        MpAccountSimpleRespVO().apply {
            id = bean.id
            name = bean.name
        }
    }

    private fun copyBase(source: MpAccountBaseVO, target: MpAccountDO) = target.apply {
        name = source.name
        account = source.account
        appId = source.appId
        url = source.url
        appSecret = source.appSecret
        token = source.token
        aesKey = source.aesKey
        remark = source.remark
    }
}
