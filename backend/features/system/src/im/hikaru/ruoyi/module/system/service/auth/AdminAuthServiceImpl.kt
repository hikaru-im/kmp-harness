package im.hikaru.ruoyi.module.system.service.auth

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.util.monitor.TracerUtils
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import im.hikaru.ruoyi.module.system.api.logger.dto.LoginLogCreateReqDTO
import im.hikaru.ruoyi.module.system.api.social.SocialUserApi
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.api.sms.SmsCodeApi
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthLoginRespVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthRegisterReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthResetPasswordReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsSendReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSocialLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.CaptchaVerificationReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
import im.hikaru.ruoyi.module.system.enums.logger.LoginResultEnum
import im.hikaru.ruoyi.module.system.enums.logger.LoginLogTypeEnum
import im.hikaru.ruoyi.module.system.enums.oauth2.OAuth2ClientConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_CAPTCHA_CODE_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.AUTH_REGISTER_CAPTCHA_CODE_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.AUTH_MOBILE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.AUTH_THIRD_LOGIN_NOT_BIND
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_MOBILE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import im.hikaru.ruoyi.module.system.service.logger.LoginLogService
import im.hikaru.ruoyi.module.system.service.member.MemberService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import com.anji.captcha.model.vo.CaptchaVO
import com.anji.captcha.service.CaptchaService
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import kotlinx.datetime.toJavaLocalDateTime

@Service
class AdminAuthServiceImpl(
    private val oauth2TokenService: OAuth2TokenService,
    private val loginLogService: LoginLogService,
    private val adminUserServiceProvider: ObjectProvider<AdminUserService>,
    private val memberServiceProvider: ObjectProvider<MemberService>,
    private val socialUserApiProvider: ObjectProvider<SocialUserApi>,
    private val captchaServiceProvider: ObjectProvider<CaptchaService>,
    private val smsCodeApiProvider: ObjectProvider<SmsCodeApi>,
    private val validator: Validator,
    @param:Value("\${yudao.captcha.enable:true}") private val captchaEnable: Boolean = true,
) : AdminAuthService {
    override fun authenticate(username: String, password: String): AdminUserDO {
        val logType = LoginLogTypeEnum.LOGIN_USERNAME
        val userService = adminUserServiceProvider.getObject()
        val user = userService.getUserByUsername(username)
        if (user == null) {
            createLoginLog(null, username, logType, LoginResultEnum.BAD_CREDENTIALS)
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS)
        }
        if (!userService.isPasswordMatch(password, requireNotNull(user.password))) {
            createLoginLog(user.id, username, logType, LoginResultEnum.BAD_CREDENTIALS)
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS)
        }
        if (CommonStatusEnum.isDisable(user.status)) {
            createLoginLog(user.id, username, logType, LoginResultEnum.USER_DISABLED)
            throw exception(AUTH_LOGIN_USER_DISABLED)
        }
        return user
    }

    @DataPermission(enable = false)
    override fun login(req: AuthLoginReqVO): AuthLoginRespVO {
        validateCaptcha(req)
        val username = requireNotNull(req.username)
        val user = authenticate(username, requireNotNull(req.password))
        req.socialType?.let { socialType ->
            socialUserApiProvider.getObject().bindSocialUser(SocialUserBindReqDTO().apply {
                userId = user.id
                userType = UserTypeEnum.ADMIN.value
                this.socialType = socialType
                code = req.socialCode
                state = req.socialState
            })
        }
        return createTokenAfterLoginSuccess(requireNotNull(user.id), username, LoginLogTypeEnum.LOGIN_USERNAME)
    }

    override fun logout(token: String, logType: Int) {
        val accessToken = oauth2TokenService.removeAccessToken(token) ?: return
        val userId = accessToken.userId
        val userType = accessToken.userType
        loginLogService.createLoginLog(LoginLogCreateReqDTO().apply {
            this.logType = logType
            traceId = TracerUtils.getTraceId()
            this.userId = userId
            this.userType = userType
            username = when (userType) {
                UserTypeEnum.ADMIN.value -> userId?.let { adminUserServiceProvider.ifAvailable?.getUser(it)?.username }
                UserTypeEnum.MEMBER.value -> memberServiceProvider.ifAvailable?.getMemberUserMobile(userId)
                else -> null
            }
            userAgent = ServletUtils.getUserAgent()
            userIp = ServletUtils.getClientIP() ?: "unknown"
            result = LoginResultEnum.SUCCESS.result
        })
    }

    override fun refreshToken(refreshToken: String): AuthLoginRespVO =
        oauth2TokenService.refreshAccessToken(refreshToken, OAuth2ClientConstants.CLIENT_ID_DEFAULT).toLoginResponse()

    override fun register(req: AuthRegisterReqVO): AuthLoginRespVO {
        validateRegisterCaptcha(req)
        val userId = adminUserServiceProvider.getObject().registerUser(req)
        return createTokenAfterLoginSuccess(userId, requireNotNull(req.username), LoginLogTypeEnum.LOGIN_USERNAME)
    }

    override fun resetPassword(req: AuthResetPasswordReqVO) {
        val mobile = requireNotNull(req.mobile)
        val user = adminUserServiceProvider.getObject().getUserByMobile(mobile)
            ?: throw exception(USER_MOBILE_NOT_EXISTS)
        smsCodeApiProvider.getObject().useSmsCode(SmsCodeUseReqDTO().apply {
            this.mobile = mobile
            scene = SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.scene
            code = req.code
            usedIp = ServletUtils.getClientIP() ?: "unknown"
        })
        adminUserServiceProvider.getObject().updateUserPassword(requireNotNull(user.id), requireNotNull(req.password))
    }

    override fun sendSmsCode(req: AuthSmsSendReqVO) {
        if (req.scene == SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.scene) validateRegisterCaptcha(req)
        val mobile = requireNotNull(req.mobile)
        if (adminUserServiceProvider.getObject().getUserByMobile(mobile) == null) {
            throw exception(AUTH_MOBILE_NOT_EXISTS)
        }
        smsCodeApiProvider.getObject().sendSmsCode(SmsCodeSendReqDTO().apply {
            this.mobile = mobile
            scene = req.scene
            createIp = ServletUtils.getClientIP() ?: "unknown"
        })
    }

    override fun smsLogin(req: AuthSmsLoginReqVO): AuthLoginRespVO {
        val mobile = requireNotNull(req.mobile)
        smsCodeApiProvider.getObject().useSmsCode(SmsCodeUseReqDTO().apply {
            this.mobile = mobile
            scene = SmsSceneEnum.ADMIN_MEMBER_LOGIN.scene
            code = req.code
            usedIp = ServletUtils.getClientIP() ?: "unknown"
        })
        val user = adminUserServiceProvider.getObject().getUserByMobile(mobile) ?: throw exception(USER_NOT_EXISTS)
        return createTokenAfterLoginSuccess(requireNotNull(user.id), mobile, LoginLogTypeEnum.LOGIN_MOBILE)
    }

    override fun socialLogin(req: AuthSocialLoginReqVO): AuthLoginRespVO {
        val socialUser = socialUserApiProvider.getObject().getSocialUserByCode(
            UserTypeEnum.ADMIN.value,
            requireNotNull(req.type),
            requireNotNull(req.code),
            requireNotNull(req.state),
        )
        val userId = socialUser?.userId ?: throw exception(AUTH_THIRD_LOGIN_NOT_BIND)
        val user = adminUserServiceProvider.getObject().getUser(userId) ?: throw exception(USER_NOT_EXISTS)
        return createTokenAfterLoginSuccess(userId, user.username.orEmpty(), LoginLogTypeEnum.LOGIN_SOCIAL)
    }

    internal fun validateCaptcha(req: CaptchaVerificationReqVO) {
        if (!captchaEnable) return
        val violations = validator.validate(req, CaptchaVerificationReqVO.CodeEnableGroup::class.java)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
        val response = captchaServiceProvider.getObject().verification(CaptchaVO().apply {
            captchaVerification = req.captchaVerification
        })
        if (!response.isSuccess) {
            val username = (req as? AuthLoginReqVO)?.username
            createLoginLog(null, username, LoginLogTypeEnum.LOGIN_USERNAME, LoginResultEnum.CAPTCHA_CODE_ERROR)
            throw exception(AUTH_LOGIN_CAPTCHA_CODE_ERROR, response.repMsg)
        }
    }

    internal fun validateRegisterCaptcha(req: CaptchaVerificationReqVO) {
        if (!captchaEnable) return
        val violations = validator.validate(req, CaptchaVerificationReqVO.CodeEnableGroup::class.java)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
        val response = captchaServiceProvider.getObject().verification(CaptchaVO().apply {
            captchaVerification = req.captchaVerification
        })
        if (!response.isSuccess) throw exception(AUTH_REGISTER_CAPTCHA_CODE_ERROR, response.repMsg)
    }

    private fun createTokenAfterLoginSuccess(userId: Long, username: String, logType: LoginLogTypeEnum): AuthLoginRespVO {
        createLoginLog(userId, username, logType, LoginResultEnum.SUCCESS)
        return oauth2TokenService.createAccessToken(
            userId,
            UserTypeEnum.ADMIN.value,
            OAuth2ClientConstants.CLIENT_ID_DEFAULT,
            emptyList(),
        ).toLoginResponse()
    }

    private fun createLoginLog(
        userId: Long?,
        username: String?,
        logType: LoginLogTypeEnum,
        loginResult: LoginResultEnum,
    ) {
        loginLogService.createLoginLog(LoginLogCreateReqDTO().apply {
            this.logType = logType.type
            traceId = TracerUtils.getTraceId()
            this.userId = userId
            userType = UserTypeEnum.ADMIN.value
            this.username = username
            userAgent = ServletUtils.getUserAgent()
            userIp = ServletUtils.getClientIP() ?: "unknown"
            result = loginResult.result
        })
        if (userId != null && loginResult == LoginResultEnum.SUCCESS) {
            adminUserServiceProvider.ifAvailable?.updateUserLogin(userId, ServletUtils.getClientIP() ?: "unknown")
        }
    }

    private fun OAuth2AccessTokenDO.toLoginResponse() = AuthLoginRespVO().apply {
        userId = this@toLoginResponse.userId
        accessToken = this@toLoginResponse.accessToken
        refreshToken = this@toLoginResponse.refreshToken
        expiresTime = this@toLoginResponse.expiresTime?.toJavaLocalDateTime()
    }
}
