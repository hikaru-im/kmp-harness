package im.hikaru.ruoyi.module.system.api.oauth2

import im.hikaru.ruoyi.framework.common.biz.system.oauth2.OAuth2TokenCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenService
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.stereotype.Service

@Service
class OAuth2TokenApiImpl(private val oauth2TokenService: OAuth2TokenService) : OAuth2TokenCommonApi {
    override fun createAccessToken(req: OAuth2AccessTokenCreateReqDTO): OAuth2AccessTokenRespDTO =
        oauth2TokenService.createAccessToken(
            requireNotNull(req.userId),
            requireNotNull(req.userType),
            requireNotNull(req.clientId),
            req.scopes.orEmpty(),
        ).toResponse()

    override fun checkAccessToken(accessToken: String): OAuth2AccessTokenCheckRespDTO =
        oauth2TokenService.checkAccessToken(accessToken).toCheckResponse()

    override fun removeAccessToken(accessToken: String): OAuth2AccessTokenRespDTO? =
        oauth2TokenService.removeAccessToken(accessToken)?.toResponse()

    override fun removeAccessToken(userId: Long, userType: Int) {
        oauth2TokenService.removeAccessToken(userId, userType)
    }

    override fun refreshAccessToken(refreshToken: String, clientId: String): OAuth2AccessTokenRespDTO =
        oauth2TokenService.refreshAccessToken(refreshToken, clientId).toResponse()

    private fun OAuth2AccessTokenDO.toResponse() = OAuth2AccessTokenRespDTO().apply {
        accessToken = this@toResponse.accessToken
        refreshToken = this@toResponse.refreshToken
        userId = this@toResponse.userId
        userType = this@toResponse.userType
        expiresTime = this@toResponse.expiresTime?.toJavaLocalDateTime()
    }

    private fun OAuth2AccessTokenDO.toCheckResponse() = OAuth2AccessTokenCheckRespDTO().apply {
        userId = this@toCheckResponse.userId
        userType = this@toCheckResponse.userType
        userInfo = this@toCheckResponse.userInfo
        tenantId = this@toCheckResponse.tenantId
        scopes = this@toCheckResponse.scopes
        expiresTime = this@toCheckResponse.expiresTime?.toJavaLocalDateTime()
    }
}
