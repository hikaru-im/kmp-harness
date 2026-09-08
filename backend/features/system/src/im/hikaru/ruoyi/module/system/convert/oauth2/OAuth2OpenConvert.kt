package im.hikaru.ruoyi.module.system.convert.oauth2

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAuthorizeInfoRespVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open.OAuth2OpenCheckTokenRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ApproveDO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ClientDO
import im.hikaru.ruoyi.module.system.util.oauth2.OAuth2Utils
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

object OAuth2OpenConvert {
    fun convert(bean: OAuth2AccessTokenDO): OAuth2OpenAccessTokenRespVO =
        requireNotNull(BeanUtils.toBean(bean, OAuth2OpenAccessTokenRespVO::class.java)).apply {
            tokenType = SecurityFrameworkUtils.AUTHORIZATION_BEARER.lowercase()
            expiresIn = bean.expiresTime?.let(OAuth2Utils::getExpiresIn)
            scope = OAuth2Utils.buildScopeStr(bean.scopes.orEmpty())
        }

    fun convert2(bean: OAuth2AccessTokenDO): OAuth2OpenCheckTokenRespVO =
        requireNotNull(BeanUtils.toBean(bean, OAuth2OpenCheckTokenRespVO::class.java)).apply {
            exp = bean.expiresTime?.toInstant(TimeZone.currentSystemDefault())?.epochSeconds
            userType = UserTypeEnum.ADMIN.value
        }

    fun convert(
        client: OAuth2ClientDO,
        approves: List<OAuth2ApproveDO>,
    ): OAuth2OpenAuthorizeInfoRespVO {
        val approveMap = approves.associateBy { it.scope }
        return OAuth2OpenAuthorizeInfoRespVO().apply {
            this.client = OAuth2OpenAuthorizeInfoRespVO.Client().apply {
                name = client.name
                logo = client.logo
            }
            scopes = client.scopes.orEmpty().map { scope ->
                KeyValue(scope, approveMap[scope]?.approved ?: false)
            }
        }
    }
}
