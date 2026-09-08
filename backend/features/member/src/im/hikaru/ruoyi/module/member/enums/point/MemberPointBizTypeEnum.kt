package im.hikaru.ruoyi.module.member.enums.point

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class MemberPointBizTypeEnum(val type: Int, val displayName: String, val description: String, val add: Boolean) {
    SIGN(1, "签到", "签到获得 {} 积分", true),
    ADMIN(2, "管理员修改", "管理员修改 {} 积分", true),
    ORDER_USE(11, "订单积分抵扣", "下单使用 {} 积分", false),
    ORDER_USE_CANCEL(12, "订单积分抵扣（整单取消）", "订单取消，退还 {} 积分", true),
    ORDER_USE_CANCEL_ITEM(13, "订单积分抵扣（单个退款）", "订单退款，退还 {} 积分", true),
    ORDER_GIVE(21, "订单积分奖励", "下单获得 {} 积分", true),
    ORDER_GIVE_CANCEL(22, "订单积分奖励（整单取消）", "订单取消，退还 {} 积分", false),
    ORDER_GIVE_CANCEL_ITEM(23, "订单积分奖励（单个退款）", "订单退款，扣除赠送的 {} 积分", false);

    companion object {
        fun getByType(type: Int?): MemberPointBizTypeEnum? = entries.firstOrNull { it.type == type }
    }
}
