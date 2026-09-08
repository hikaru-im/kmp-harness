package im.hikaru.ruoyi.module.system.controller.admin.auth

import im.hikaru.contracts.auth.AuthLoginResponse
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthLoginRespVO

internal fun AuthLoginRespVO.toContract() = AuthLoginResponse(
    userId = userId,
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresTime = expiresTime?.toString(),
)
