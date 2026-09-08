package im.hikaru.ruoyi.module.member.api.point

import im.hikaru.ruoyi.module.member.enums.point.MemberPointBizTypeEnum
import jakarta.validation.constraints.Min

interface MemberPointApi {
    fun addPoint(userId: Long, point: Int, bizType: Int, bizId: String): Unit
    fun reducePoint(userId: Long, point: Int, bizType: Int, bizId: String): Unit
}
