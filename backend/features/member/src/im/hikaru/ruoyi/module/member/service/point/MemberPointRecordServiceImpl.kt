package im.hikaru.ruoyi.module.member.service.point

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.point.vo.recrod.MemberPointRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.app.point.vo.AppMemberPointRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.point.MemberPointRecordDO
import im.hikaru.ruoyi.module.member.dal.mysql.point.MemberPointRecordDao
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_POINT_NOT_ENOUGH
import im.hikaru.ruoyi.module.member.enums.point.MemberPointBizTypeEnum
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberPointRecordServiceImpl(
    private val memberUserService: MemberUserService,
) : MemberPointRecordService {
    override fun getPointRecordPage(pageReqVO: MemberPointRecordPageReqVO): PageResult<MemberPointRecordDO> {
        val userIds = pageReqVO.nickname?.takeIf { it.isNotBlank() }?.let { nickname ->
            val users = memberUserService.getUserListByNickname(nickname)
            if (users.isEmpty()) return PageResult.empty()
            users.mapNotNull { it.id }.toSet()
        }
        return MemberPointRecordDao.selectPage(pageReqVO, userIds)
    }
    override fun getPointRecordPage(userId: Long, pageReqVO: AppMemberPointRecordPageReqVO): PageResult<MemberPointRecordDO> =
        MemberPointRecordDao.selectPage(userId, pageReqVO)

    @Transactional(rollbackFor = [Exception::class])
    override fun createPointRecord(userId: Long, point: Int, bizType: MemberPointBizTypeEnum, bizId: String) {
        if (point == 0) return
        val user = memberUserService.getUser(userId) ?: return
        val totalPoint = (user.point ?: 0) + point
        if (totalPoint < 0 || !memberUserService.updateUserPoint(userId, point)) throw exception(USER_POINT_NOT_ENOUGH)
        MemberPointRecordDao.insert(MemberPointRecordDO().apply {
            this.userId = userId; this.bizId = bizId; this.bizType = bizType.type
            title = bizType.displayName; description = bizType.description.replace("{}", point.toString())
            this.point = point; this.totalPoint = totalPoint
        })
    }
}
