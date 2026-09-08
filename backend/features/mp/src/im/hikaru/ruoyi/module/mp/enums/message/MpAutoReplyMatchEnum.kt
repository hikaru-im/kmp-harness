package im.hikaru.ruoyi.module.mp.enums.message

enum class MpAutoReplyMatchEnum(val match: Int, val displayName: String) {
    ALL(1, "完全匹配"),
    LIKE(2, "半匹配");
}
