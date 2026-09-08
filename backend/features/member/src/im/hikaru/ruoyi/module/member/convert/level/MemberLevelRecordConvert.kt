package im.hikaru.ruoyi.module.member.convert.level

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.record.MemberLevelRecordRespVO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelDO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelRecordDO

object MemberLevelRecordConvert {
    fun convert(source: MemberLevelRecordDO): MemberLevelRecordRespVO = requireNotNull(BeanUtils.toBean(source, MemberLevelRecordRespVO::class.java))
    fun convertList(source: List<MemberLevelRecordDO>): List<MemberLevelRecordRespVO> = BeanUtils.toBean(source, MemberLevelRecordRespVO::class.java) ?: emptyList()
    fun convertPage(source: PageResult<MemberLevelRecordDO>): PageResult<MemberLevelRecordRespVO> = requireNotNull(BeanUtils.toBean(source, MemberLevelRecordRespVO::class.java))
    fun copyTo(from: MemberLevelDO, to: MemberLevelRecordDO): MemberLevelRecordDO = to.apply {
        levelId = from.id; level = from.level; discountPercent = from.discountPercent
    }
}
