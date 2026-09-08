package im.hikaru.ruoyi.module.mp.enums.message

enum class MpMessageSendFromEnum(val from: Int, val displayName: String) {
    USER_TO_MP(1, "粉丝发送给公众号"),
    MP_TO_USER(2, "公众号发给粉丝");
}
