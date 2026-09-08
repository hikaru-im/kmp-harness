package im.hikaru.ruoyi.module.mp.convert.tag

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.MpTagSimpleRespVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.tag.MpTagDO
import kotlinx.datetime.toJavaLocalDateTime
import me.chanjar.weixin.mp.bean.tag.WxUserTag

object MpTagConvert {
    fun convert(tag: WxUserTag, account: MpAccountDO): MpTagDO = MpTagDO().apply {
        tagId = tag.id
        name = tag.name
        count = tag.count
        accountId = account.id
        appId = account.appId
    }

    fun convert(bean: MpTagDO): MpTagRespVO = MpTagRespVO().apply {
        id = bean.id
        name = bean.name
        count = bean.count
        createTime = bean.createTime?.toJavaLocalDateTime()
    }

    fun convertPage(page: PageResult<MpTagDO>) = PageResult(
        total = page.total,
        list = page.list.map(::convert),
    )

    fun convertSimpleList(list: List<MpTagDO>): List<MpTagSimpleRespVO> = list.map { bean ->
        MpTagSimpleRespVO().apply {
            id = bean.id
            tagId = bean.tagId
            name = bean.name
        }
    }
}
