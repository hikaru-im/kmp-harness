package im.hikaru.ruoyi.module.member.service.auth

import im.hikaru.ruoyi.module.member.controller.app.auth.vo.*
import jakarta.validation.Valid

interface MemberAuthService {
    fun login(@Valid reqVO: AppAuthLoginReqVO): AppAuthLoginRespVO
    fun logout(token: String): Unit
    fun smsLogin(@Valid reqVO: AppAuthSmsLoginReqVO): AppAuthLoginRespVO
    fun socialLogin(@Valid reqVO: AppAuthSocialLoginReqVO): AppAuthLoginRespVO
    fun weixinMiniAppLogin(reqVO: AppAuthWeixinMiniAppLoginReqVO): AppAuthLoginRespVO
    fun getSocialAuthorizeUrl(type: Int, redirectUri: String): String
    fun sendSmsCode(userId: Long, reqVO: AppAuthSmsSendReqVO): Unit
    fun validateSmsCode(userId: Long, reqVO: AppAuthSmsValidateReqVO): Unit
    fun refreshToken(refreshToken: String): AppAuthLoginRespVO
}
