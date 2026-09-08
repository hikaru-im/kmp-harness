package im.hikaru.ruoyi.module.infra.enums.codegen

enum class CodegenSceneEnum(
    val scene: Int,
    val displayName: String,
    val basePackage: String,
    val prefixClass: String,
) {
    ADMIN(1, "Admin", "admin", ""),
    APP(2, "App", "app", "App"),
    ;

    companion object {
        fun fromScene(scene: Int?): CodegenSceneEnum? = entries.firstOrNull { it.scene == scene }
    }
}
