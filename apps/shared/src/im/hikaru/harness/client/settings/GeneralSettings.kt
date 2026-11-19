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

/**
 * Codex `settings.general.enterBehavior.description.v2` ("Choose when Enter sends a prompt or inserts
 * a new line"). That key ships without a zh-CN entry, so the shipped locale has no Chinese string for
 * it — the wording here is the v2 description, not the older `.description` toggle sentence.
 */
public const val ENTER_BEHAVIOR_DESCRIPTION: String = "选择按 Enter 时是发送提示还是插入新行"

/** Only preferences with an actual Desktop consumer belong in the saved document. */
@Serializable
public data class GeneralSettings(
    val closeConfirmation: CloseConfirmationPolicy = CloseConfirmationPolicy.KeyboardOnly,
    /** Missing in documents written before SET-10; decoding must keep those documents loadable. */
    val enterBehavior: EnterBehavior = EnterBehavior.Enter,
)

// Rows this build does not wire yet stay visibly disabled and carry no reason text: Codex's own
// settings surface has no 「尚未接入…」 copy, and a disabled control is its own explanation.
