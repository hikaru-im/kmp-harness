package im.hikaru.ruoyi.framework.common.biz.system.oauth2

import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO
import jakarta.validation.Valid

interface OAuth2TokenCommonApi {
    fun createAccessToken(@Valid req: OAuth2AccessTokenCreateReqDTO): OAuth2AccessTokenRespDTO
    fun checkAccessToken(accessToken: String): OAuth2AccessTokenCheckRespDTO
    fun removeAccessToken(accessToken: String): OAuth2AccessTokenRespDTO?
    fun refreshAccessToken(refreshToken: String, clientId: String): OAuth2AccessTokenRespDTO
}
