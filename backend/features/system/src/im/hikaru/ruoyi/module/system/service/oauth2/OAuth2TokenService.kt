package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO

interface OAuth2TokenService {
    fun createAccessToken(userId: Long, userType: Int, clientId: String, scopes: List<String>): OAuth2AccessTokenDO
    fun refreshAccessToken(refreshToken: String, clientId: String): OAuth2AccessTokenDO
    fun getAccessToken(accessToken: String): OAuth2AccessTokenDO?
    fun checkAccessToken(accessToken: String): OAuth2AccessTokenDO
    fun removeAccessToken(accessToken: String): OAuth2AccessTokenDO?
    fun removeAccessToken(userId: Long, userType: Int)
    fun getAccessTokenPage(req: OAuth2AccessTokenPageReqVO): PageResult<OAuth2AccessTokenDO>
    fun cleanRefreshToken(exceedDay: Int, deleteLimit: Int): Int
    fun cleanAccessToken(exceedDay: Int, deleteLimit: Int): Int
}
