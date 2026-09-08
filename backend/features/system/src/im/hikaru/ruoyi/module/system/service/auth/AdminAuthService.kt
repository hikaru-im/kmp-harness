package im.hikaru.ruoyi.module.system.service.auth

import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthLoginRespVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthRegisterReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthResetPasswordReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsSendReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSocialLoginReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO

interface AdminAuthService {
    fun authenticate(username: String, password: String): AdminUserDO
    fun login(req: AuthLoginReqVO): AuthLoginRespVO
    fun logout(token: String, logType: Int)
    fun refreshToken(refreshToken: String): AuthLoginRespVO
    fun register(req: AuthRegisterReqVO): AuthLoginRespVO
    fun resetPassword(req: AuthResetPasswordReqVO)
    fun sendSmsCode(req: AuthSmsSendReqVO)
    fun smsLogin(req: AuthSmsLoginReqVO): AuthLoginRespVO
    fun socialLogin(req: AuthSocialLoginReqVO): AuthLoginRespVO
}
