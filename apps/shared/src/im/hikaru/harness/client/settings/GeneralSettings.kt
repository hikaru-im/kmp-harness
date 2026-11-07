package im.hikaru.harness.client.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class CloseConfirmationPolicy(public val label: String) {
    @SerialName("never") Never("从不确认"),
    @SerialName("keyboardOnly") KeyboardOnly("仅关闭快捷键"),
    @SerialName("always") Always("每次关闭"),
}

/**
 * Codex `settings.general.enterBehavior` (SET-10). The wire names match the reference's stored
 * values so an existing document keeps meaning the same thing.
 */
@Serializable
public enum class EnterBehavior {
    @SerialName("enter") Enter,
    @SerialName("cmdAlways") CmdAlways,
    @SerialName("cmdIfMultiline") CmdIfMultiline,
}

/** The platform's submit chord symbol: ⌘ on macOS, Ctrl elsewhere (Codex `modifierSymbol`). */
public fun enterModifierChord(platform: ShortcutPlatform): String = ShortcutChord(
    key = ShortcutKey.Enter,
    ctrl = platform != ShortcutPlatform.Mac,
    meta = platform == ShortcutPlatform.Mac,
).label(platform)

/** Codex `settings.general.enterBehavior.*` copy, with the platform modifier substituted. */
public fun EnterBehavior.label(platform: ShortcutPlatform): String {
    // Codex's settings copy spaces the chord (`⌘ + Enter`), while the composer hint keeps the
    // compact `⌘+Enter` form. Align the row/menu copy with the settings reference.
    val chord = enterModifierChord(platform).replace("+", " + ")
    return when (this) {
        EnterBehavior.Enter -> "按 Enter 键"
        EnterBehavior.CmdAlways -> "总是使用 $chord"
        EnterBehavior.CmdIfMultiline -> "$chord 用于多行提示"
    }
}

/** Codex `settings.general.enterBehavior.description.v2`. */
public const val ENTER_BEHAVIOR_DESCRIPTION: String = "选择按 Enter 时是发送提示还是插入新行"

/** Only preferences with an actual Desktop consumer belong in the saved document. */
@Serializable
public data class GeneralSettings(
    val closeConfirmation: CloseConfirmationPolicy = CloseConfirmationPolicy.KeyboardOnly,
    /** Missing in documents written before SET-10; decoding must keep those documents loadable. */
    val enterBehavior: EnterBehavior = EnterBehavior.Enter,
)

/** Current runtime limitations, separate from the native reference's visual fixture values. */
public object GeneralUnavailableReasons {
    public fun forRow(id: String): String? = when (id) {
        "default-permission", "full-disk-access" -> "尚未接入 Host 权限配置"
        "task-folder" -> "尚未接入无项目任务目录"
        "default-file-app" -> "尚未接入文件打开偏好"
        "language" -> "当前界面使用中文，尚未接入语言切换"
        "menu-bar" -> "尚未接入菜单栏驻留"
        "full-view", "bottom-panel" -> "将在会话面板接入时启用"
        "terminal-location" -> "将在终端面板接入时启用"
        "prevent-sleep" -> "尚未接入系统唤醒服务"
        "compress-history" -> "尚未接入会话存储压缩"
        "prompt-suggestions" -> "尚未接入提示词建议服务"
        "licenses" -> "尚未生成本客户端的第三方声明"
        else -> null
    }
}
