package im.hikaru.ruoyi.module.member.api.point

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.POINT_RECORD_BIZ_NOT_SUPPORT
import im.hikaru.ruoyi.module.member.enums.point.MemberPointBizTypeEnum
import im.hikaru.ruoyi.module.member.service.point.MemberPointRecordService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberPointApiImpl(
    private val memberPointRecordService: MemberPointRecordService,
) : MemberPointApi {
    override fun addPoint(userId: Long, point: Int, bizType: Int, bizId: String) {
        require(point > 0)
        val type = MemberPointBizTypeEnum.getByType(bizType) ?: throw exception(POINT_RECORD_BIZ_NOT_SUPPORT)
        memberPointRecordService.createPointRecord(userId, point, type, bizId)
    }

    override fun reducePoint(userId: Long, point: Int, bizType: Int, bizId: String) {
        require(point > 0)
        val type = MemberPointBizTypeEnum.getByType(bizType) ?: throw exception(POINT_RECORD_BIZ_NOT_SUPPORT)
        memberPointRecordService.createPointRecord(userId, -point, type, bizId)
    }
}
