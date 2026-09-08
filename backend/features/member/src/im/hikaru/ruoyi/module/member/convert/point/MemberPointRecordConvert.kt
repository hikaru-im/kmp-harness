package im.hikaru.ruoyi.module.member.convert.point

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.controller.admin.point.vo.recrod.MemberPointRecordRespVO
import im.hikaru.ruoyi.module.member.controller.app.point.vo.AppMemberPointRecordRespVO
import im.hikaru.ruoyi.module.member.dal.dataobject.point.MemberPointRecordDO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO

object MemberPointRecordConvert {
    fun convertPage(source: PageResult<MemberPointRecordDO>): PageResult<MemberPointRecordRespVO> = requireNotNull(BeanUtils.toBean(source, MemberPointRecordRespVO::class.java))
    fun convertPage(source: PageResult<MemberPointRecordDO>, users: List<MemberUserDO>): PageResult<MemberPointRecordRespVO> {
        val result = convertPage(source)
        val map = users.mapNotNull { user -> user.id?.let { id -> id to user.nickname } }.toMap()
        result.list.forEach { it.nickname = map[it.userId] }
        return result
    }
    fun convertPage02(source: PageResult<MemberPointRecordDO>): PageResult<AppMemberPointRecordRespVO> = requireNotNull(BeanUtils.toBean(source, AppMemberPointRecordRespVO::class.java))
}
