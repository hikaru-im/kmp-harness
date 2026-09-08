package im.hikaru.ruoyi.module.member.convert.signin

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.record.MemberSignInRecordRespVO
import im.hikaru.ruoyi.module.member.controller.app.signin.vo.record.AppMemberSignInRecordRespVO
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInConfigDO
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInRecordDO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

object MemberSignInRecordConvert {
    fun convertPage(source: PageResult<MemberSignInRecordDO>, users: List<MemberUserDO>): PageResult<MemberSignInRecordRespVO> {
        val userMap = users.associateBy { it.id }
        return PageResult(
            total = source.total,
            list = source.list.map { record ->
                requireNotNull(BeanUtils.toBean(record, MemberSignInRecordRespVO::class.java)).apply {
                    nickname = record.userId?.let(userMap::get)?.nickname
                }
            },
        )
    }

    fun convertPage(source: PageResult<MemberSignInRecordDO>): PageResult<AppMemberSignInRecordRespVO> =
        PageResult(source.total, source.list.map(::convert))

    fun convert(source: MemberSignInRecordDO): AppMemberSignInRecordRespVO =
        requireNotNull(BeanUtils.toBean(source, AppMemberSignInRecordRespVO::class.java))

    fun convert(
        userId: Long,
        lastRecord: MemberSignInRecordDO?,
        configs: List<MemberSignInConfigDO>,
        currentDate: LocalDate,
    ): MemberSignInRecordDO {
        val sorted = configs.sortedBy { it.day ?: Int.MAX_VALUE }
        val lastDate = lastRecord?.signDate ?: lastRecord?.createTime?.date
        var day = if (lastDate == currentDate.minus(1, DateTimeUnit.DAY)) {
            (lastRecord?.day ?: 0) + 1
        } else {
            1
        }
        val maxDay = sorted.lastOrNull()?.day
        if (maxDay != null && day > maxDay) day = 1
        val config = sorted.firstOrNull { it.day == day }
        return MemberSignInRecordDO().apply {
            this.userId = userId
            this.day = day
            point = config?.point ?: 0
            experience = config?.experience ?: 0
            signDate = currentDate
        }
    }
}
