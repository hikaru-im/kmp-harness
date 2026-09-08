package im.hikaru.ruoyi.module.infra.enums.codegen

enum class CodegenColumnListConditionEnum(val condition: String) {
    EQ("="), NE("!="), GT(">"), GTE(">="), LT("<"), LTE("<="), LIKE("LIKE"), BETWEEN("BETWEEN")
}
