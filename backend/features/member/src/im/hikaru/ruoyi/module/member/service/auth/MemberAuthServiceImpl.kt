package im.hikaru.ruoyi.module.member.service.auth

import im.hikaru.ruoyi.framework.common.biz.system.oauth2.OAuth2TokenCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.TerminalEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.util.monitor.TracerUtils
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthLoginReqVO
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthLoginRespVO
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthSmsLoginReqVO
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthSmsSendReqVO
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthSmsValidateReqVO
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthSocialLoginReqVO
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthWeixinMiniAppLoginReqVO
import im.hikaru.ruoyi.module.member.convert.auth.AuthConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.AUTH_MOBILE_USED
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.AUTH_SOCIAL_USER_NOT_FOUND
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_MOBILE_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import im.hikaru.ruoyi.module.system.api.logger.LoginLogApi
import im.hikaru.ruoyi.module.system.api.logger.dto.LoginLogCreateReqDTO
import im.hikaru.ruoyi.module.system.api.sms.SmsCodeApi
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeValidateReqDTO
import im.hikaru.ruoyi.module.system.api.social.SocialClientApi
import im.hikaru.ruoyi.module.system.api.social.SocialUserApi
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.enums.logger.LoginLogTypeEnum
import im.hikaru.ruoyi.module.system.enums.logger.LoginResultEnum
import im.hikaru.ruoyi.module.system.enums.oauth2.OAuth2ClientConstants
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberAuthServiceImpl(
    private val userService: MemberUserService,
    private val smsCodeApi: SmsCodeApi,
    private val loginLogApi: LoginLogApi,
    private val socialUserApi: SocialUserApi,
    private val socialClientApi: SocialClientApi,
    private val oauth2TokenApi: OAuth2TokenCommonApi,
) : MemberAuthService {

    override fun login(reqVO: AppAuthLoginReqVO): AppAuthLoginRespVO {
        val mobile = requireNotNull(reqVO.mobile)
        val user = authenticate(mobile, requireNotNull(reqVO.password))
        val openid = reqVO.socialType?.let { type ->
            socialUserApi.bindSocialUser(bindRequest(user, type, reqVO.socialCode, reqVO.socialState))
        }
        return createTokenAfterLoginSuccess(user, mobile, LoginLogTypeEnum.LOGIN_MOBILE, openid)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun smsLogin(reqVO: AppAuthSmsLoginReqVO): AppAuthLoginRespVO {
        val mobile = requireNotNull(reqVO.mobile)
        smsCodeApi.useSmsCode(SmsCodeUseReqDTO().apply {
            this.mobile = mobile
            code = reqVO.code
            scene = SmsSceneEnum.MEMBER_LOGIN.scene
            usedIp = clientIp()
        })
        val user = userService.createUserIfAbsent(mobile, clientIp(), WebFrameworkUtils.getTerminal())
        validateUserEnabled(user, mobile, LoginLogTypeEnum.LOGIN_SMS)
        val openid = reqVO.socialType?.let { type ->
            socialUserApi.bindSocialUser(bindRequest(user, type, reqVO.socialCode, reqVO.socialState))
        }
        return createTokenAfterLoginSuccess(user, mobile, LoginLogTypeEnum.LOGIN_SMS, openid)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun socialLogin(reqVO: AppAuthSocialLoginReqVO): AppAuthLoginRespVO {
        val type = requireNotNull(reqVO.type)
        val socialUser = socialUserApi.getSocialUserByCode(
            UserTypeEnum.MEMBER.value,
            type,
            requireNotNull(reqVO.code),
            requireNotNull(reqVO.state),
        ) ?: throw exception(AUTH_SOCIAL_USER_NOT_FOUND)
        val user = socialUser.userId?.let(userService::getUser) ?: userService.createUser(
            socialUser.nickname,
            socialUser.avatar,
            clientIp(),
            WebFrameworkUtils.getTerminal(),
        ).also { socialUserApi.bindSocialUser(bindRequest(it, type, reqVO.code, reqVO.state)) }
        val requiredUser = user ?: throw exception(USER_NOT_EXISTS)
        validateUserEnabled(requiredUser, requiredUser.mobile, LoginLogTypeEnum.LOGIN_SOCIAL)
        return createTokenAfterLoginSuccess(requiredUser, requiredUser.mobile, LoginLogTypeEnum.LOGIN_SOCIAL, socialUser.openid)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun weixinMiniAppLogin(reqVO: AppAuthWeixinMiniAppLoginReqVO): AppAuthLoginRespVO {
        val phone = socialClientApi.getWxMaPhoneNumberInfo(
            UserTypeEnum.MEMBER.value,
            requireNotNull(reqVO.phoneCode),
        )?.purePhoneNumber ?: error("Weixin did not return a phone number")
        val user = userService.createUserIfAbsent(phone, clientIp(), TerminalEnum.WECHAT_MINI_PROGRAM.terminal)
        validateUserEnabled(user, phone, LoginLogTypeEnum.LOGIN_SOCIAL)
        val openid = socialUserApi.bindSocialUser(bindRequest(
            user,
            SocialTypeEnum.WECHAT_MINI_PROGRAM.type,
            reqVO.loginCode,
            reqVO.state,
        ))
        return createTokenAfterLoginSuccess(user, phone, LoginLogTypeEnum.LOGIN_SOCIAL, openid)
    }

    override fun getSocialAuthorizeUrl(type: Int, redirectUri: String): String =
        socialClientApi.getAuthorizeUrl(type, UserTypeEnum.MEMBER.value, redirectUri)

    override fun logout(token: String) {
        val removed = oauth2TokenApi.removeAccessToken(token) ?: return
        createLoginLog(removed.userId, removed.userId?.let(userService::getUser)?.mobile, LoginLogTypeEnum.LOGOUT_SELF, LoginResultEnum.SUCCESS)
    }

    override fun sendSmsCode(userId: Long, reqVO: AppAuthSmsSendReqVO) {
        when (reqVO.scene) {
            SmsSceneEnum.MEMBER_UPDATE_MOBILE.scene -> {
                userService.getUserByMobile(requireNotNull(reqVO.mobile))?.let {
                    if (it.id != userId) throw exception(AUTH_MOBILE_USED)
                }
            }
            SmsSceneEnum.MEMBER_RESET_PASSWORD.scene -> {
                if (userService.getUserByMobile(requireNotNull(reqVO.mobile)) == null) throw exception(USER_MOBILE_NOT_EXISTS)
            }
            SmsSceneEnum.MEMBER_UPDATE_PASSWORD.scene -> {
                reqVO.mobile = userService.getUser(userId)?.mobile ?: throw exception(USER_NOT_EXISTS)
            }
        }
        smsCodeApi.sendSmsCode(SmsCodeSendReqDTO().apply {
            mobile = reqVO.mobile
            scene = reqVO.scene
            createIp = clientIp()
        })
    }

    override fun validateSmsCode(userId: Long, reqVO: AppAuthSmsValidateReqVO) {
        if (reqVO.mobile.isNullOrBlank() && userId > 0) reqVO.mobile = userService.getUser(userId)?.mobile
        smsCodeApi.validateSmsCode(SmsCodeValidateReqDTO().apply {
            mobile = reqVO.mobile
            scene = reqVO.scene
            code = reqVO.code
        })
    }

    override fun refreshToken(refreshToken: String): AppAuthLoginRespVO = AuthConvert.convert(
        oauth2TokenApi.refreshAccessToken(refreshToken, OAuth2ClientConstants.CLIENT_ID_DEFAULT),
        null,
    )

    private fun authenticate(mobile: String, password: String): MemberUserDO {
        val user = userService.getUserByMobile(mobile)
        if (user == null || user.password == null || !userService.isPasswordMatch(password, requireNotNull(user.password))) {
            createLoginLog(user?.id, mobile, LoginLogTypeEnum.LOGIN_MOBILE, LoginResultEnum.BAD_CREDENTIALS)
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS)
        }
        validateUserEnabled(user, mobile, LoginLogTypeEnum.LOGIN_MOBILE)
        return user
    }

    private fun validateUserEnabled(user: MemberUserDO, mobile: String?, type: LoginLogTypeEnum) {
        if (CommonStatusEnum.isDisable(user.status)) {
            createLoginLog(user.id, mobile, type, LoginResultEnum.USER_DISABLED)
            throw exception(AUTH_LOGIN_USER_DISABLED)
        }
    }

    private fun createTokenAfterLoginSuccess(
        user: MemberUserDO,
        mobile: String?,
        logType: LoginLogTypeEnum,
        openid: String?,
    ): AppAuthLoginRespVO {
        createLoginLog(user.id, mobile, logType, LoginResultEnum.SUCCESS)
        val token = oauth2TokenApi.createAccessToken(OAuth2AccessTokenCreateReqDTO().apply {
            userId = user.id
            userType = UserTypeEnum.MEMBER.value
            clientId = OAuth2ClientConstants.CLIENT_ID_DEFAULT
        })
        return AuthConvert.convert(token, openid)
    }

    private fun createLoginLog(
        userId: Long?,
        mobile: String?,
        logType: LoginLogTypeEnum,
        result: LoginResultEnum,
    ) {
        loginLogApi.createLoginLog(LoginLogCreateReqDTO().apply {
            this.logType = logType.type
            traceId = TracerUtils.getTraceId()
            this.userId = userId
            userType = UserTypeEnum.MEMBER.value
            username = mobile
            userAgent = ServletUtils.getUserAgent()
            userIp = clientIp()
            this.result = result.result
        })
        if (userId != null && result == LoginResultEnum.SUCCESS && logType.type < 200) {
            userService.updateUserLogin(userId, clientIp())
        }
    }

    private fun bindRequest(user: MemberUserDO, type: Int, code: String?, state: String?) = SocialUserBindReqDTO().apply {
        userId = user.id
        userType = UserTypeEnum.MEMBER.value
        socialType = type
        this.code = code
        this.state = state
    }

    private fun clientIp(): String = ServletUtils.getClientIP() ?: "unknown"
}
