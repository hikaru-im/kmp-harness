package im.hikaru.ruoyi.module.member.service.point

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.point.vo.recrod.MemberPointRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.app.point.vo.AppMemberPointRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.point.MemberPointRecordDO
import im.hikaru.ruoyi.module.member.enums.point.MemberPointBizTypeEnum

interface MemberPointRecordService {
    fun getPointRecordPage(pageReqVO: MemberPointRecordPageReqVO): PageResult<MemberPointRecordDO>
    fun getPointRecordPage(userId: Long, pageReqVO: AppMemberPointRecordPageReqVO): PageResult<MemberPointRecordDO>
    fun createPointRecord(userId: Long, point: Int, bizType: MemberPointBizTypeEnum, bizId: String): Unit
}
