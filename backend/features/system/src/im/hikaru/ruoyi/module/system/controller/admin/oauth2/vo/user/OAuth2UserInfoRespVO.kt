package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.user

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Admin - OAuth2 user information response")
class OAuth2UserInfoRespVO {
    var id: Long? = null
    var username: String? = null
    var nickname: String? = null
    var email: String? = null
    var mobile: String? = null
    var sex: Int? = null
    var avatar: String? = null
    var dept: Dept? = null
    var posts: List<Post> = emptyList()

    class Dept {
        var id: Long? = null
        var name: String? = null
    }

    class Post {
        var id: Long? = null
        var name: String? = null
    }
}
