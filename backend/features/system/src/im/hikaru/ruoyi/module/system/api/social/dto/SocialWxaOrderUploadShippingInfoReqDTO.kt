package im.hikaru.ruoyi.module.system.api.social.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class SocialWxaOrderUploadShippingInfoReqDTO {
    @field:NotEmpty(message = "Openid must not be empty")
    var openid: String? = null

    @field:NotEmpty(message = "Transaction id must not be empty")
    var transactionId: String? = null

    @field:NotNull(message = "Logistics type must not be null")
    var logisticsType: Int? = null
    var logisticsNo: String? = null
    var expressCompany: String? = null

    @field:NotEmpty(message = "Item description must not be empty")
    var itemDesc: String? = null

    @field:NotEmpty(message = "Receiver contact must not be empty")
    var receiverContact: String? = null

    companion object {
        const val LOGISTICS_TYPE_EXPRESS: Int = 1
        const val LOGISTICS_TYPE_VIRTUAL: Int = 3
        const val LOGISTICS_TYPE_PICK_UP: Int = 4
    }
}
