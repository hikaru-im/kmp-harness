package im.hikaru.ruoyi.module.pay.api.transfer.dto

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.common.validation.InEnum
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class PayTransferCreateReqDTO {
    @field:NotNull(message = "应用标识不能为空")
    var appKey: String? = null
    @field:NotEmpty(message = "用户 IP 不能为空")
    var userIp: String? = null
    var userId: Long? = null
    @field:InEnum(UserTypeEnum::class)
    var userType: Int? = null
    @field:NotEmpty(message = "商户转账单编号能为空")
    var merchantTransferId: String? = null
    @field:Min(value = 1, message = "转账金额必须大于零")
    @field:NotNull(message = "转账金额不能为空")
    var price: Int? = null
    @field:NotEmpty(message = "转账标题不能为空")
    var subject: String? = null
    @field:NotEmpty(message = "收款人账号不能为空")
    var userAccount: String? = null
    var userName: String? = null
    @field:NotEmpty(message = "转账渠道不能为空")
    var channelCode: String? = null
    var channelExtras: Map<String, String>? = null

    companion object {
        fun buildWeiXinChannelExtra1000(activityName: String, rewardDescription: String): Map<String, String> =
            buildWeiXinChannelExtra(1000, "activityName" to activityName, "rewardDescription" to rewardDescription)

        fun buildWeiXinChannelExtra1006(expenseType: String, expenseDescription: String): Map<String, String> =
            buildWeiXinChannelExtra(1006, "expenseType" to expenseType, "expenseDescription" to expenseDescription)

        fun buildAlipayChannelExtra(sceneName: String): Map<String, String> = mapOf("sceneName" to sceneName)

        private fun buildWeiXinChannelExtra(sceneId: Int, vararg values: Pair<String, String>): Map<String, String> =
            mapOf(
                "sceneId" to sceneId.toString(),
                "sceneReportInfos" to JsonUtils.toJsonString(values.map { (type, content) ->
                    mapOf("infoType" to type, "infoContent" to content)
                }),
            )
    }
}
