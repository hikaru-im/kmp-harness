package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open

import im.hikaru.ruoyi.framework.common.core.KeyValue
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Admin - OAuth2 authorization page information")
class OAuth2OpenAuthorizeInfoRespVO {
    var client: Client? = null
    var scopes: List<KeyValue<String, Boolean>> = emptyList()

    class Client {
        var name: String? = null
        var logo: String? = null
    }
}
