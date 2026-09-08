package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ClientDO
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ClientDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CLIENT_AUTHORIZED_GRANT_TYPE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CLIENT_CLIENT_SECRET_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CLIENT_DISABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CLIENT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CLIENT_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CLIENT_REDIRECT_URI_NOT_MATCH
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_CLIENT_SCOPE_OVER
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable

@Service
class OAuth2ClientServiceImpl : OAuth2ClientService {
    override fun createOAuth2Client(req: OAuth2ClientSaveReqVO): Long {
        validateClientIdExists(null, requireNotNull(req.clientId))
        return OAuth2ClientDao.insert(req.toEntity())
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.OAUTH_CLIENT], allEntries = true)
    override fun updateOAuth2Client(req: OAuth2ClientSaveReqVO) {
        val id = requireNotNull(req.id)
        validateOAuth2ClientExists(id)
        validateClientIdExists(id, requireNotNull(req.clientId))
        OAuth2ClientDao.updateById(req.toEntity())
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.OAUTH_CLIENT], allEntries = true)
    override fun deleteOAuth2Client(id: Long) {
        validateOAuth2ClientExists(id)
        OAuth2ClientDao.deleteById(id)
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.OAUTH_CLIENT], allEntries = true)
    override fun deleteOAuth2ClientList(ids: List<Long>) {
        OAuth2ClientDao.deleteByIds(ids)
    }

    override fun getOAuth2Client(id: Long) = OAuth2ClientDao.selectById(id)

    @Cacheable(cacheNames = [RedisKeyConstants.OAUTH_CLIENT], key = "#clientId", unless = "#result == null")
    override fun getOAuth2ClientFromCache(clientId: String) = OAuth2ClientDao.selectByClientId(clientId)

    override fun getOAuth2ClientPage(req: OAuth2ClientPageReqVO) = OAuth2ClientDao.selectPage(req)

    override fun validOAuthClientFromCache(
        clientId: String,
        clientSecret: String?,
        authorizedGrantType: String?,
        scopes: Collection<String>?,
        redirectUri: String?,
    ): OAuth2ClientDO {
        val client = getOAuth2ClientFromCache(clientId) ?: throw exception(OAUTH2_CLIENT_NOT_EXISTS)
        if (CommonStatusEnum.isDisable(client.status)) throw exception(OAUTH2_CLIENT_DISABLE)
        if (!clientSecret.isNullOrEmpty() && client.secret != clientSecret) {
            throw exception(OAUTH2_CLIENT_CLIENT_SECRET_ERROR, clientSecret)
        }
        if (!authorizedGrantType.isNullOrEmpty() && authorizedGrantType !in client.authorizedGrantTypes.orEmpty()) {
            throw exception(OAUTH2_CLIENT_AUTHORIZED_GRANT_TYPE_NOT_EXISTS)
        }
        if (!scopes.isNullOrEmpty() && !client.scopes.orEmpty().containsAll(scopes)) {
            throw exception(OAUTH2_CLIENT_SCOPE_OVER)
        }
        if (!redirectUri.isNullOrEmpty() && client.redirectUris.orEmpty().none { redirectUri.startsWith(it) }) {
            throw exception(OAUTH2_CLIENT_REDIRECT_URI_NOT_MATCH, redirectUri)
        }
        return client
    }

    private fun validateOAuth2ClientExists(id: Long) {
        if (OAuth2ClientDao.selectById(id) == null) throw exception(OAUTH2_CLIENT_NOT_EXISTS)
    }

    internal fun validateClientIdExists(id: Long?, clientId: String) {
        OAuth2ClientDao.selectByClientId(clientId)?.let {
            if (it.id != id) throw exception(OAUTH2_CLIENT_EXISTS)
        }
    }

    private fun OAuth2ClientSaveReqVO.toEntity() = OAuth2ClientDO().apply {
        id = this@toEntity.id
        clientId = this@toEntity.clientId
        secret = this@toEntity.secret
        name = this@toEntity.name
        logo = this@toEntity.logo
        description = this@toEntity.description
        status = this@toEntity.status
        accessTokenValiditySeconds = this@toEntity.accessTokenValiditySeconds
        refreshTokenValiditySeconds = this@toEntity.refreshTokenValiditySeconds
        redirectUris = this@toEntity.redirectUris
        authorizedGrantTypes = this@toEntity.authorizedGrantTypes
        scopes = this@toEntity.scopes
        autoApproveScopes = this@toEntity.autoApproveScopes
        authorities = this@toEntity.authorities
        resourceIds = this@toEntity.resourceIds
        additionalInformation = this@toEntity.additionalInformation
    }
}
