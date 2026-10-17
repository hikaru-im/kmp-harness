package im.hikaru.harness.client.app

import im.hikaru.harness.client.conversation.ConversationAction
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.client.connection.ModelMessageSource

public data class ConversationModelChoice(
    val providerId: String, val modelId: String, val label: String,
    val reasoningEfforts: List<String> = emptyList(),
)

public enum class ComposerPermission(val label: String, val description: String) {
    Ask("请求批准", "编辑外部文件和使用互联网时始终询问"),
    Automatic("帮我批准", "仅对检测到的风险操作请求批准"),
    FullAccess("完全访问权限", "可不受限制地访问互联网和你电脑上的任何文件"),
    // Measured fourth row of the live menu. Like every other entry this is a UI preference only.
    Custom("自定义 (config.toml)", "使用 config.toml 中定义的权限"),
}

/** UI selection only: permission is deliberately never serialized into AgentOptions. */
public data class ComposerSessionPreferences(
    val permission: ComposerPermission = ComposerPermission.Ask,
    val model: ConversationModelChoice? = null,
    val effort: String? = null,
    val modelConfigurationKnown: Boolean = false,
)

internal enum class ComposerPrimaryAction { Send, Busy, Stop, Stopping }

internal fun ConversationState.composerPrimaryAction(): ComposerPrimaryAction = when {
    cancelling -> ComposerPrimaryAction.Stopping
    canCancel -> ComposerPrimaryAction.Stop
    action != ConversationAction.Idle -> ComposerPrimaryAction.Busy
    else -> ComposerPrimaryAction.Send
}

internal enum class ComposerPopup { Closed, Permissions, Reasoning, Models }

internal data class ComposerFeedback(val text: String, val tag: String, val configureModels: Boolean = false)

/** Runtime progress wins over setup hints; one explanation owns the feedback slot. */
internal fun composerFeedback(
    state: ConversationState, modelReason: String?, submitReason: String?, noModels: Boolean, hostDefault: Boolean,
): ComposerFeedback? = when {
    !state.connected -> ComposerFeedback(submitReason ?: "连接 Host 后可发送消息。",
        if (submitReason == null) "live-composer-disconnected" else "live-composer-submit-unavailable")
    state.cancelling -> ComposerFeedback("正在停止…", "live-composer-action")
    state.action != ConversationAction.Idle -> ComposerFeedback(when (state.action) {
        ConversationAction.Creating -> "正在创建会话…"
        ConversationAction.Associating -> "正在确认项目关联…"
        ConversationAction.Sending -> "正在发送…"
        ConversationAction.WaitingForLocalIdle -> "正在运行…"
        ConversationAction.RunningUnknown -> "消息已接收，运行状态尚未确认。"
        ConversationAction.Idle -> ""
    }, "live-composer-action")
    state.selectedSessionId == null && noModels && !hostDefault ->
        ComposerFeedback(modelReason ?: "请先配置模型。", "live-composer-model-unavailable", configureModels = true)
    submitReason != null -> ComposerFeedback(submitReason, "live-composer-submit-unavailable")
    else -> null
}

internal fun composerModel(
    state: ConversationState, choices: List<ConversationModelChoice>, preferences: ComposerSessionPreferences?,
): ConversationModelChoice? {
    preferences?.model?.let { return it }
    val source = state.history.mapNotNull { it.source as? ModelMessageSource }.lastOrNull() ?: return null
    return choices.firstOrNull { it.providerId == source.provider && it.modelId == source.model }
        ?: ConversationModelChoice(source.provider, source.model, "${source.provider} / ${source.model}")
}

/**
 * Live Codex effort names (`composer.mode.local.reasoning.*.label`, zh-CN), read from the asar and
 * confirmed by the picker's sr-only label list: 无 / 极低 / 轻度 / 中 / 高 / 极高 / Max / Ultra / 持续.
 * `max` and `ultra` have no zh-CN translation in the bundle, so they stay Latin.
 */
internal fun effortLabel(value: String?): String = when (value) {
    null -> "默认"
    "none" -> "无"
    "minimal" -> "极低"
    "low" -> "轻度"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "极高"
    "max" -> "Max"
    "ultra" -> "Ultra"
    "persistent" -> "持续"
    else -> value
}

internal fun ConversationModelChoice.menuKey(): String =
    "conversation-model:${providerId.length}:$providerId:${modelId.length}:$modelId"
