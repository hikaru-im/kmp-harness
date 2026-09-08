package im.hikaru.ruoyi.module.system.api.social.dto

import jakarta.validation.constraints.NotEmpty

class SocialWxQrcodeReqDTO {
    @field:NotEmpty(message = "Scene must not be empty")
    var scene: String? = null

    @field:NotEmpty(message = "Path must not be empty")
    var path: String? = null
    var width: Int? = null
    var autoColor: Boolean? = null
    var checkPath: Boolean? = null
    var hyaline: Boolean? = null

    companion object {
        const val SCENE: String = ""
        const val WIDTH: Int = 430
        const val AUTO_COLOR: Boolean = true
        const val CHECK_PATH: Boolean = true
        const val HYALINE: Boolean = true
    }
}
