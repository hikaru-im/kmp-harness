package im.hikaru.ruoyi.framework.apilog.core.enums

enum class OperateTypeEnum(val type: Int) {
    GET(1),
    CREATE(2),
    UPDATE(3),
    DELETE(4),
    EXPORT(5),
    IMPORT(6),
    OTHER(0),
}
