package im.hikaru.ruoyi.module.mp.enums.message

enum class MpAutoReplyTypeEnum(val type: Int, val displayName: String) {
    SUBSCRIBE(1, "关注时回复"),
    MESSAGE(2, "收到消息回复"),
    KEYWORD(3, "关键词回复");
}
