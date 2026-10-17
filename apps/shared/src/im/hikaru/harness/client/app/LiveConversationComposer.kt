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
    /** Blocks submit without printing a hint: the model chip already shows that a model is needed. */
    submitBlocked: Boolean = false,
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
    val canSend = state.canSubmit && submitUnavailableReason == null && !submitBlocked &&
        state.composerPrimaryAction() == ComposerPrimaryAction.Send
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
            // Live `_ComposerLayoutRoot`: rgb(54,54,54) in dark, radius 22, no outer border, and a
            // `rgba(255,255,255,.2) 0 0 1px 0 inset` glow — approximated by a 0.5dp light ring.
            color = ConversationAppearance.composer,
            contentColor = ConversationAppearance.ink,
            shape = RoundedCornerShape(HarnessTokens.Composer.Radius),
            border = BorderStroke(HarnessTokens.Composer.BorderWidth, ConversationAppearance.composerGlow),
        ) {
            Column(Modifier.padding(top = HarnessTokens.Composer.PaddingTop, bottom = HarnessTokens.Composer.PaddingBottom),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.SectionGap)) {
                BasicTextField(
                    value = editor,
                    onValueChange = { value ->
                        val previousText = editor.text
                        editor = value
                        if (value.text != previousText) onDraftChange(value.text)
                    },
                    // Live editor: the wrapper keeps `0 12px`; the thread input row measures 44 high.
                    modifier = Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Composer.PaddingX)
                        .heightIn(min = minOf(if (welcome) HarnessTokens.Composer.WelcomeEditorMinHeight
                        else HarnessTokens.Composer.ThreadEditorMinHeight, editorCap), max = editorCap)
                        .onFocusChanged { if (!it.isFocused) enterHeld = false }
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
                    // Live `ProseMirror`: 14 / 20, white ink; placeholder `与 Codex 协作` in 0.498 white.
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ConversationAppearance.ink,
                        fontSize = HarnessTokens.Composer.TextSize, lineHeight = HarnessTokens.Composer.TextLineHeight),
                    cursorBrush = SolidColor(ConversationAppearance.ink),
                    singleLine = false,
                    decorationBox = { inner ->
                        Box {
                            if (editor.text.isEmpty()) HarnessText(if (welcome) "随心输入" else "与 Codex 协作",
                                color = ConversationAppearance.tertiary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = HarnessTokens.Composer.TextSize,
                                    lineHeight = HarnessTokens.Composer.TextLineHeight))
                            inner()
                        }
                    },
                )
                ConversationComposerToolbar(state, models, selectedModel, onModelSelect, modelUnavailableReason, canSend, onSubmit, onCancel,
                    selectedReasoningEffort, onReasoningEffortSelect, reasoningEffortKnown, useHostDefaultModel,
                    permission, onPermissionSelect, onConfigureModels, draftEmpty = editor.text.isBlank())
                val notices = Modifier.padding(horizontal = HarnessTokens.Composer.PaddingX)
                composerFeedback(state, modelUnavailableReason, submitUnavailableReason, models.isEmpty(), useHostDefaultModel)?.let { feedback ->
                    if (feedback.configureModels && onConfigureModels != null) {
                        Row(notices.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HarnessText(feedback.text, Modifier.weight(1f).semantics { testTag = feedback.tag },
                                color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            HarnessButton("配置模型", onConfigureModels, variant = HarnessButtonVariant.Ghost,
                                size = HarnessControlSize.Compact, testTag = "live-composer-configure-models")
                        }
                    } else ConversationNotice(feedback.text, feedback.tag, modifier = notices)
                }
                // Home has no timeline slot: preserve create/prompt/cancel errors beside its draft.
                state.errors.values.filter { it.kind in listOf(ConversationErrorKind.Create, ConversationErrorKind.Association, ConversationErrorKind.Prompt,
                    ConversationErrorKind.Cancel, ConversationErrorKind.Completion) }.forEach {
                    ConversationNotice(it.message, "live-composer-error-${it.kind.name}", isError = true, modifier = notices)
                }
            }
        }
    }
}

