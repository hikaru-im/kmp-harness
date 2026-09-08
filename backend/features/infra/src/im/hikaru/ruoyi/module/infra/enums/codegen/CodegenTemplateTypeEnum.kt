package im.hikaru.ruoyi.module.infra.enums.codegen

enum class CodegenTemplateTypeEnum(val type: Int) {
    ONE(1), TREE(2), MASTER_NORMAL(10), MASTER_ERP(11), MASTER_INNER(12), SUB(15);

    companion object {
        fun isMaster(type: Int?): Boolean = type in setOf(MASTER_NORMAL.type, MASTER_ERP.type, MASTER_INNER.type)
        fun isTree(type: Int?): Boolean = type == TREE.type
    }
}
