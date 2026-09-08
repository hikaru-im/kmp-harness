package im.hikaru.ruoyi.module.mp.convert.message

import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateRespVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageTemplateDO
import kotlinx.datetime.toJavaLocalDateTime

object MpMessageTemplateConvert {
    fun convert(bean: MpMessageTemplateDO): MpMessageTemplateRespVO = MpMessageTemplateRespVO().apply {
        id = bean.id
        accountId = bean.accountId
        appId = bean.appId
        templateId = bean.templateId
        title = bean.title
        content = bean.content
        example = bean.example
        primaryIndustry = bean.primaryIndustry
        deputyIndustry = bean.deputyIndustry
        createTime = bean.createTime?.toJavaLocalDateTime()
    }

    fun convertList(list: List<MpMessageTemplateDO>): List<MpMessageTemplateRespVO> = list.map(::convert)
}
