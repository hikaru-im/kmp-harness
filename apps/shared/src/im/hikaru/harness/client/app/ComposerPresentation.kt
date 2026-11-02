package im.hikaru.harness.client.app

import im.hikaru.harness.client.conversation.ConversationAction
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.client.connection.ModelMessageSource
import im.hikaru.harness.client.connection.ReasoningEffortId
import im.hikaru.harness.client.connection.ReasoningOptions
import im.hikaru.harness.client.settings.ModelReasoningCapabilities
import im.hikaru.harness.client.settings.reasoningOptionsError

public data class ConversationModelChoice(
    val providerId: String, val modelId: String, val label: String,
    val reasoningEfforts: List<String> = emptyList(),
    val reasoningCapabilities: im.hikaru.harness.client.settings.ModelReasoningCapabilities? = null,
)

/** One resolved value drives both the displayed effort and the request payload. */
internal fun resolveComposerReasoning(model: ConversationModelChoice?, requested: ReasoningOptions): ReasoningOptions =
    if (model != null && reasoningOptionsError(requested, model.effectiveReasoningCapabilities()) == null) requested
    else ReasoningOptions()

/** The effort shortcut must respect the same combination rules as the full editor. */
internal fun ConversationModelChoice.allowedReasoningEfforts(options: ReasoningOptions?): List<String> =
    reasoningEfforts.filter(String::isNotBlank).distinct().filter { effort ->
        options == null || reasoningOptionsError(options.copy(effort = ReasoningEffortId(effort)), effectiveReasoningCapabilities()) == null
    }

private fun ConversationModelChoice.effectiveReasoningCapabilities(): ModelReasoningCapabilities =
    reasoningCapabilities ?: ModelReasoningCapabilities(efforts = reasoningEfforts)

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
    val reasoning: ReasoningOptions? = null,
)

internal enum class ComposerPrimaryAction { Send, Busy, Stop, Stopping }

internal fun ConversationState.composerPrimaryAction(): ComposerPrimaryAction = when {
    cancelling -> ComposerPrimaryAction.Stopping
    canCancel -> ComposerPrimaryAction.Stop
    action != ConversationAction.Idle -> ComposerPrimaryAction.Busy
    else -> ComposerPrimaryAction.Send
}

internal enum class ComposerPopup { Closed, Permissions, Reasoning, Models }

internal fun composerModel(
    state: ConversationState, choices: List<ConversationModelChoice>, preferences: ComposerSessionPreferences?,
): ConversationModelChoice? {
    preferences?.model?.let { chosen ->
        return choices.firstOrNull { it.providerId == chosen.providerId && it.modelId == chosen.modelId } ?: chosen
    }
    state.modelConfiguration?.let { config ->
        if (config.provider != null && config.model != null) return choices.firstOrNull {
            it.providerId == config.provider && it.modelId == config.model
        } ?: ConversationModelChoice(config.provider, config.model, config.model)
    }
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


/** Keep disabled models in settings/runtime while exposing only enabled chat choices. */
internal fun im.hikaru.harness.client.settings.ModelSettingsSnapshot.composerModelChoices(): List<ConversationModelChoice> {
    val candidates = providers.filter { it.active && it.applicationError == null && !it.applying }.flatMap { provider ->
        provider.models.filter { it.id !in provider.disabledModelIds && it.selectable }.map { provider to it }
    }
    val counts = candidates.groupingBy { it.second.name }.eachCount()
    return candidates.map { (provider, model) ->
        ConversationModelChoice(provider.id, model.id,
            if (counts.getValue(model.name) > 1) model.name + " · " + provider.displayName else model.name,
            model.reasoningEfforts, model.reasoningCapabilities)
    }
}
