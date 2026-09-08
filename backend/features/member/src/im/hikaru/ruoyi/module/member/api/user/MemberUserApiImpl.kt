package im.hikaru.ruoyi.module.member.api.user

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.api.user.dto.MemberUserRespDTO
import im.hikaru.ruoyi.module.member.convert.user.MemberUserConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_MOBILE_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberUserApiImpl(
    private val userService: MemberUserService,
) : MemberUserApi {
    override fun getUser(id: Long): MemberUserRespDTO =
        MemberUserConvert.convertDto(userService.getUser(id) ?: throw exception(USER_NOT_EXISTS))

    override fun getUserList(ids: Collection<Long>): List<MemberUserRespDTO> =
        MemberUserConvert.convertDtoList(userService.getUserList(ids))

    override fun getUserListByNickname(nickname: String): List<MemberUserRespDTO> =
        MemberUserConvert.convertDtoList(userService.getUserListByNickname(nickname))

    override fun getUserByMobile(mobile: String): MemberUserRespDTO =
        MemberUserConvert.convertDto(userService.getUserByMobile(mobile) ?: throw exception(USER_MOBILE_NOT_EXISTS))

    override fun validateUser(id: Long) {
        if (userService.getUser(id) == null) throw exception(USER_NOT_EXISTS)
    }
}
