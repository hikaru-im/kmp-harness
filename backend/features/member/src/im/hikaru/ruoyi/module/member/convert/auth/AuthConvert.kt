package im.hikaru.ruoyi.module.member.convert.auth

import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthLoginRespVO

object AuthConvert {
    fun convert(token: OAuth2AccessTokenRespDTO, openid: String?): AppAuthLoginRespVO = AppAuthLoginRespVO().apply {
        userId = token.userId
        accessToken = token.accessToken
        refreshToken = token.refreshToken
        expiresTime = token.expiresTime
        this.openid = openid
    }
}
