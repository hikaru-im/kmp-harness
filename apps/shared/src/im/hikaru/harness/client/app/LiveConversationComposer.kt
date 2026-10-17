package im.hikaru.harness.client.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import im.hikaru.harness.client.conversation.ConversationErrorKind
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.ui.foundation.*

@Composable
public fun LiveConversationComposer(
    state: ConversationState,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    models: List<ConversationModelChoice>,
    selectedModel: ConversationModelChoice?,
    onModelSelect: (ConversationModelChoice) -> Unit,
    modelUnavailableReason: String?,
    submitUnavailableReason: String?,
    selectedReasoningEffort: String? = null,
    onReasoningEffortSelect: ((String?) -> Unit)? = null,
    onConfigureModels: (() -> Unit)? = null,
    useHostDefaultModel: Boolean = false,
    permission: ComposerPermission,
    onPermissionSelect: (ComposerPermission) -> Unit,
    reasoningEffortKnown: Boolean = state.selectedSessionId == null,
) {
    val configuredCap = LocalConversationEditorMaxHeight.current.coerceAtLeast(HarnessTokens.Composer.ThreadEditorMinHeight)
    val suppliedViewport = LocalConversationViewportHeight.current
    val canSend = state.canSubmit && submitUnavailableReason == null && state.composerPrimaryAction() == ComposerPrimaryAction.Send
    val latestCanSend by rememberUpdatedState(canSend)
    val latestSubmit by rememberUpdatedState(onSubmit)
    val hostId = state.host?.hostId
    val identity = state.host?.identity
    val sessionId = state.selectedSessionId
    val welcome = sessionId == null
    var editor by remember(hostId, identity, sessionId) {
        mutableStateOf(TextFieldValue(state.draft, TextRange(state.draft.length)))
    }
    var enterHeld by remember(hostId, identity, sessionId) { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (!windowFocused) enterHeld = false }
    LaunchedEffect(hostId, identity, sessionId, state.draft) {
        if (editor.text != state.draft) editor = TextFieldValue(state.draft,
            TextRange(editor.selection.start.coerceIn(0, state.draft.length), editor.selection.end.coerceIn(0, state.draft.length)))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewport = suppliedViewport ?: if (constraints.hasBoundedHeight) maxHeight else null
        val editorCap = if (viewport == null) configuredCap else minOf(configuredCap,
            (viewport * HarnessTokens.Composer.ViewportFraction).coerceAtLeast(HarnessTokens.Composer.ThreadEditorMinHeight))
        Surface(
            Modifier.fillMaxWidth().semantics { testTag = "live-composer" },
            color = HarnessTokens.Colors.SurfaceElevated,
            contentColor = HarnessTokens.Colors.Text,
            shape = RoundedCornerShape(HarnessTokens.Composer.Radius),
            border = BorderStroke(HarnessTokens.Composer.BorderWidth, if (editorFocused)
                HarnessTokens.Colors.BorderFocused.copy(alpha = HarnessTokens.Composer.FocusAlpha) else HarnessTokens.Colors.SurfaceRing),
        ) {
            Column(Modifier.padding(start = HarnessTokens.Composer.PaddingX, end = HarnessTokens.Composer.PaddingX,
                top = HarnessTokens.Composer.PaddingTop, bottom = HarnessTokens.Composer.PaddingBottom),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.SectionGap)) {
                BasicTextField(
                    value = editor,
                    onValueChange = { value ->
                        val previousText = editor.text
                        editor = value
                        if (value.text != previousText) onDraftChange(value.text)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = minOf(if (welcome) HarnessTokens.Composer.WelcomeEditorMinHeight
                        else HarnessTokens.Composer.ThreadEditorMinHeight, editorCap), max = editorCap)
                        .onFocusChanged { editorFocused = it.isFocused; if (!it.isFocused) enterHeld = false }
                        .onPreviewKeyEvent { event ->
                            when {
                                event.key != Key.Enter && event.key != Key.NumPadEnter -> false
                                event.type == KeyEventType.KeyUp -> {
                                    val consumed = enterHeld
                                    enterHeld = false
                                    consumed
                                }
                                event.type != KeyEventType.KeyDown -> false
                                enterHeld -> true
                                event.isShiftPressed || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed -> false
                                editor.composition != null -> false
                                else -> {
                                    enterHeld = true
                                    if (latestCanSend) latestSubmit()
                                    true
                                }
                            }
                        }
                        .semantics { testTag = "live-composer-editor"; contentDescription = "消息" },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = HarnessTokens.Colors.Text),
                    cursorBrush = SolidColor(HarnessTokens.Colors.Text),
                    singleLine = false,
                    decorationBox = { inner ->
                        Box {
                            if (editor.text.isEmpty()) HarnessText(if (welcome) "描述你想构建的内容…" else "继续提问，或补充任务细节…", color = HarnessTokens.Colors.TextMuted)
                            inner()
                        }
                    },
                )
                ConversationComposerToolbar(state, models, selectedModel, onModelSelect, modelUnavailableReason, canSend, onSubmit, onCancel,
                    selectedReasoningEffort, onReasoningEffortSelect, reasoningEffortKnown, useHostDefaultModel,
                    permission, onPermissionSelect, onConfigureModels)
                composerFeedback(state, modelUnavailableReason, submitUnavailableReason, models.isEmpty(), useHostDefaultModel)?.let { feedback ->
                    if (feedback.configureModels && onConfigureModels != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HarnessText(feedback.text, Modifier.weight(1f).semantics { testTag = feedback.tag },
                                color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            HarnessButton("配置模型", onConfigureModels, variant = HarnessButtonVariant.Ghost,
                                size = HarnessControlSize.Compact, testTag = "live-composer-configure-models")
                        }
                    } else ConversationNotice(feedback.text, feedback.tag)
                }
                // Home has no timeline slot: preserve create/prompt/cancel errors beside its draft.
                state.errors.values.filter { it.kind in listOf(ConversationErrorKind.Create, ConversationErrorKind.Association, ConversationErrorKind.Prompt,
                    ConversationErrorKind.Cancel, ConversationErrorKind.Completion) }.forEach {
                    ConversationNotice(it.message, "live-composer-error-${it.kind.name}", isError = true)
                }
            }
        }
    }
}

