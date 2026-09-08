package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ClientDO

interface OAuth2ClientService {
    fun createOAuth2Client(req: OAuth2ClientSaveReqVO): Long
    fun updateOAuth2Client(req: OAuth2ClientSaveReqVO)
    fun deleteOAuth2Client(id: Long)
    fun deleteOAuth2ClientList(ids: List<Long>)
    fun getOAuth2Client(id: Long): OAuth2ClientDO?
    fun getOAuth2ClientFromCache(clientId: String): OAuth2ClientDO?
    fun getOAuth2ClientPage(req: OAuth2ClientPageReqVO): PageResult<OAuth2ClientDO>

    fun validOAuthClientFromCache(clientId: String): OAuth2ClientDO =
        validOAuthClientFromCache(clientId, null, null, null, null)

    fun validOAuthClientFromCache(
        clientId: String,
        clientSecret: String?,
        authorizedGrantType: String?,
        scopes: Collection<String>?,
        redirectUri: String?,
    ): OAuth2ClientDO
}
