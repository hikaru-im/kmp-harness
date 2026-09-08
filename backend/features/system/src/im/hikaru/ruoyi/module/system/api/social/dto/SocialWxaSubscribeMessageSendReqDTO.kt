package im.hikaru.ruoyi.module.system.api.social.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class SocialWxaSubscribeMessageSendReqDTO {
    @field:NotNull(message = "User id must not be null")
    var userId: Long? = null

    @field:NotNull(message = "User type must not be null")
    var userType: Int? = null

    @field:NotEmpty(message = "Template title must not be empty")
    var templateTitle: String? = null
    var page: String? = null
    var messages: MutableMap<String, String>? = null

    fun addMessage(key: String, value: String): SocialWxaSubscribeMessageSendReqDTO {
        if (messages == null) messages = linkedMapOf()
        messages!![key] = value
        return this
    }
}
