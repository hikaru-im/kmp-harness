package im.hikaru.ruoyi.module.member.service.signin

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.record.MemberSignInRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.app.signin.vo.record.AppMemberSignInRecordSummaryRespVO
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInRecordDO
import kotlinx.datetime.LocalDate

interface MemberSignInRecordService {
    fun getSignInRecordPage(pageReqVO: MemberSignInRecordPageReqVO): PageResult<MemberSignInRecordDO>
    fun getSignRecordPage(userId: Long, pageParam: PageParam): PageResult<MemberSignInRecordDO>
    fun createSignRecord(userId: Long): MemberSignInRecordDO
    fun createSignRecord(userId: Long, requestedDate: LocalDate): SignInMutationResult
    fun getSignInRecordSummary(userId: Long): AppMemberSignInRecordSummaryRespVO
}

sealed interface SignInMutationResult {
    data class Applied(
        val record: MemberSignInRecordDO,
        val summary: AppMemberSignInRecordSummaryRespVO,
    ) : SignInMutationResult

    data object AlreadySigned : SignInMutationResult
    data class DateNotCurrent(val currentDate: LocalDate) : SignInMutationResult
    data object UserNotFound : SignInMutationResult
}
