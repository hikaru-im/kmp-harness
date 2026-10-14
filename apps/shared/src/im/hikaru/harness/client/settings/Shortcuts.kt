package im.hikaru.harness.client.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public enum class ShortcutPlatform { Mac, Windows, Linux, Unavailable }

/** The complete compile-time command catalog for this batch; each entry has a real consumer. */
@Serializable
public enum class ShortcutCommand(public val label: String, public val description: String) {
    @SerialName("settings") Settings("打开设置", "打开常规设置"),
    @SerialName("showKeyboardShortcuts") ShowKeyboardShortcuts("键盘快捷键", "打开快捷键设置"),
    @SerialName("toggleSidebar") ToggleSidebar("切换侧栏", "展开或收起当前页面的侧栏"),
    @SerialName("navigateBack") NavigateBack("返回", "返回上一个页面"),
    @SerialName("closeWindow") CloseWindow("关闭窗口", "按常规设置中的确认策略关闭窗口"),
    @SerialName("manageTasks") ManageTasks("管理定时任务", "打开定时任务页面"),
}

/** One physical key, never a sequence or a modifier by itself. */
@Serializable
public enum class ShortcutKey(public val label: String) {
    A("A"), B("B"), C("C"), D("D"), E("E"), F("F"), G("G"), H("H"), I("I"), J("J"),
    K("K"), L("L"), M("M"), N("N"), O("O"), P("P"), Q("Q"), R("R"), S("S"), T("T"),
    U("U"), V("V"), W("W"), X("X"), Y("Y"), Z("Z"),
    Digit0("0"), Digit1("1"), Digit2("2"), Digit3("3"), Digit4("4"), Digit5("5"),
    Digit6("6"), Digit7("7"), Digit8("8"), Digit9("9"),
    Comma(","), Slash("/"), LeftBracket("["), RightBracket("]"), Period("."),
    F1("F1"), F2("F2"), F3("F3"), F4("F4"), F5("F5"), F6("F6"),
    F7("F7"), F8("F8"), F9("F9"), F10("F10"), F11("F11"), F12("F12"),
    Escape("Esc"), Enter("Enter"), Space("Space"), Tab("Tab"), Backspace("Backspace"),
    Left("←"), Right("→"), Up("↑"), Down("↓"),
}

@Serializable
public data class ShortcutChord(
    val key: ShortcutKey,
    val ctrl: Boolean = false,
    val meta: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    public fun label(platform: ShortcutPlatform): String = buildList {
        if (ctrl) add("Ctrl")
        if (meta) add(if (platform == ShortcutPlatform.Mac) "⌘" else "Meta")
        if (alt) add(if (platform == ShortcutPlatform.Mac) "⌥" else "Alt")
        if (shift) add("Shift")
        add(key.label)
    }.joinToString(" + ")
}

/** Absent override = defaults; empty override = explicitly disabled. */
@Serializable
public data class ShortcutsSettings(
    val schemaVersion: Int = 1,
    val overrides: Map<ShortcutCommand, List<ShortcutChord>> = emptyMap(),
) {
    public fun bindings(command: ShortcutCommand, platform: ShortcutPlatform): List<ShortcutChord> =
        overrides[command] ?: ShortcutRegistry.defaults(command, platform)

    public fun withBindings(command: ShortcutCommand, bindings: List<ShortcutChord>): ShortcutsSettings =
        copy(overrides = overrides + (command to bindings.toList()))

    public fun reset(command: ShortcutCommand): ShortcutsSettings = copy(overrides = overrides - command)
}

public object ShortcutRegistry {
    public val commands: List<ShortcutCommand> = ShortcutCommand.entries.toList()

    public fun defaults(command: ShortcutCommand, platform: ShortcutPlatform): List<ShortcutChord> {
        if (platform == ShortcutPlatform.Unavailable) return emptyList()
        val key = when (command) {
            ShortcutCommand.Settings -> ShortcutKey.Comma
            ShortcutCommand.ShowKeyboardShortcuts -> ShortcutKey.Slash
            ShortcutCommand.ToggleSidebar -> ShortcutKey.S
            ShortcutCommand.NavigateBack -> ShortcutKey.LeftBracket
            ShortcutCommand.CloseWindow -> ShortcutKey.W
            ShortcutCommand.ManageTasks -> return emptyList()
        }
        return listOf(ShortcutChord(key, ctrl = platform != ShortcutPlatform.Mac, meta = platform == ShortcutPlatform.Mac,
            shift = command == ShortcutCommand.ToggleSidebar))
    }

    public fun chordError(chord: ShortcutChord): String? {
        if (!chord.ctrl && !chord.meta && !chord.alt) return "请使用 Ctrl、Command 或 Alt 加一个按键；不支持单键或仅 Shift。"
        // Keep editing, formatting, Find and OS window/quit shortcuts in their original contexts.
        if ((chord.ctrl || chord.meta) && chord.key in setOf(
                ShortcutKey.A, ShortcutKey.B, ShortcutKey.C, ShortcutKey.F, ShortcutKey.G, ShortcutKey.I,
                ShortcutKey.U, ShortcutKey.V, ShortcutKey.X, ShortcutKey.Y, ShortcutKey.Z, ShortcutKey.Q,
                ShortcutKey.H, ShortcutKey.M,
            )) return "此组合保留给查找、编辑、格式或系统操作。"
        if (chord.key == ShortcutKey.Escape || chord.key == ShortcutKey.F3 || (chord.alt && chord.key == ShortcutKey.F4) ||
            (chord.meta && chord.key == ShortcutKey.Space) || (chord.alt && chord.key == ShortcutKey.Tab) ||
            (chord.ctrl && chord.alt && chord.key == ShortcutKey.Backspace)) return "此组合保留给查找或系统操作。"
        return null
    }

    public fun validationError(settings: ShortcutsSettings, platform: ShortcutPlatform): String? {
        if (settings.schemaVersion != 1) return "不支持此快捷键配置版本。"
        val owners = mutableMapOf<ShortcutChord, ShortcutCommand>()
        for (command in commands) {
            val bindings = settings.bindings(command, platform)
            if (bindings.size != bindings.distinct().size) return "同一命令的组合不能重复。"
            for (chord in bindings) {
                chordError(chord)?.let { return it }
                val owner = owners.put(chord, command)
                if (owner != null && owner != command) return "此组合已用于“${owner.label}”。"
            }
        }
        return null
    }
}
