package im.hikaru.ruoyi.module.member.enums

enum class MemberExperienceBizTypeEnum(val type: Int, val title: String, val description: String, val add: Boolean) {
    ADMIN(0, "管理员调整", "管理员调整获得 {} 经验", true),
    INVITE_REGISTER(1, "邀新奖励", "邀请好友获得 {} 经验", true),
    SIGN_IN(4, "签到奖励", "签到获得 {} 经验", true),
    LOTTERY(5, "抽奖奖励", "抽奖获得 {} 经验", true),
    ORDER_GIVE(11, "下单奖励", "下单获得 {} 经验", true),
    ORDER_GIVE_CANCEL(12, "下单奖励（整单取消）", "取消订单获得 {} 经验", false),
    ORDER_GIVE_CANCEL_ITEM(13, "下单奖励（单个退款）", "退款订单获得 {} 经验", false);

    companion object {
        fun getByType(type: Int?): MemberExperienceBizTypeEnum? = entries.firstOrNull { it.type == type }
    }
}
