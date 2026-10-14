@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.app.ConversationModelChoice
import im.hikaru.harness.client.app.LiveConversationComposer
import im.hikaru.harness.client.app.LiveHomeSurface
import im.hikaru.harness.client.app.LiveThreadSurface
import im.hikaru.harness.client.app.LocalConversationViewportHeight
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.ModelMessageSource
import im.hikaru.harness.client.connection.ReasoningContent
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.ToolCallContent
import im.hikaru.harness.client.connection.ToolMessageSource
import im.hikaru.harness.client.connection.ToolResultContent
import im.hikaru.harness.client.connection.UserMessageSource
import im.hikaru.harness.client.conversation.CompletionCapability
import im.hikaru.harness.client.conversation.ConversationDataPhase
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.conversation.ConversationRunState
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.client.panels.ThreadPanelScope
import im.hikaru.harness.client.panels.ThreadPanelTab
import im.hikaru.harness.client.panels.ThreadPanels
import im.hikaru.harness.client.panels.ThreadPanelsState
import im.hikaru.harness.client.panels.reduce
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test-only data rendered by the shared live surfaces; no runtime host or model is contacted. */
class LiveConversationScreenshotTest {
    @Test
    fun captureHomeAtContractSizesInBothThemes() {
        captureMatrix("home") {
            LiveHomeSurface(hostLabel = "Screenshot fixture · local Host") {
                FixtureComposer(homeState())
            }
        }
    }

    @Test
    fun captureTypedThreadAtContractSizesInBothThemes() {
        captureMatrix("thread-typed") { size ->
            CompositionLocalProvider(LocalConversationViewportHeight provides size.height.dp) {
                val state = threadState()
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LiveThreadSurface(state, onRetry = { unexpectedAction("history retry") })
                    }
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        FixtureComposer(state)
                    }
                }
            }
        }
    }

    @Test
    fun captureTerminalUnavailableAtContractSizesInBothThemes() {
        captureMatrix("terminal-unavailable") {
            FixturePanels(ThreadPanelTab.BackgroundOutput)
        }
    }

    @Test
    fun captureSubagentsUnavailableAtContractSizesInBothThemes() {
        captureMatrix("subagents-unavailable") {
            FixturePanels(ThreadPanelTab.Subagents)
        }
    }

    @Composable
    private fun FixtureComposer(initialState: ConversationState) {
        var state by remember(initialState) { mutableStateOf(initialState) }
        var model by remember { mutableStateOf(fixtureModels.first()) }
        LiveConversationComposer(
            state = state,
            onDraftChange = { state = state.copy(draft = it, draftRevision = state.draftRevision + 1) },
            onSubmit = { unexpectedAction("submit") },
            onCancel = { unexpectedAction("cancel") },
            models = fixtureModels,
            selectedModel = model,
            onModelSelect = { model = it },
            modelUnavailableReason = if (state.selectedSessionId != null) "已有会话沿用创建时的模型。" else null,
            submitUnavailableReason = null,
        )
    }

    @Composable
    private fun FixturePanels(tab: ThreadPanelTab) {
        var state by remember(tab) {
            mutableStateOf(ThreadPanelsState(scope = fixturePanelScope, selectedTab = tab))
        }
        // Defaults explicitly represent missing services, without fabricated output or children.
        ThreadPanels(state = state, onAction = { state = state.reduce(it) })
    }

    private fun homeState(): ConversationState = ConversationState(
        host = fixtureHost,
        connected = true,
        listPhase = ConversationDataPhase.Empty,
        historyPhase = ConversationDataPhase.Empty,
        runState = ConversationRunState.Idle,
        completionCapability = CompletionCapability.LocalIdle,
    )

    private fun threadState(): ConversationState = homeState().copy(
        listPhase = ConversationDataPhase.Ready,
        historyPhase = ConversationDataPhase.Ready,
        selectedSessionId = fixtureSessionId,
        history = typedHistory(),
        draft = "请继续检查紧凑布局。\n保留真实会话状态与模型选择限制。",
    )

    private fun typedHistory(): List<Message> = listOf(
        Message(
            id = "screenshot-user-1",
            role = MessageRole.USER,
            content = listOf(TextContent("检查共享会话界面，展示 reasoning、工具调用和工具结果。")),
            source = UserMessageSource,
        ),
        Message(
            id = "screenshot-assistant-1",
            role = MessageRole.ASSISTANT,
            content = listOf(
                TextContent("先读取界面文件，再确认 typed history 的渲染路径。"),
                ReasoningContent("这段推理仅为截图测试数据；使用生产 renderer 的默认折叠状态。"),
                ToolCallContent(
                    id = "screenshot-read-1",
                    name = "read_file",
                    arguments = "{\"path\":\"fixture/Conversation.kt\"}",
                ),
            ),
            source = ModelMessageSource(provider = "screenshot-provider", model = "screenshot-model"),
        ),
        Message(
            id = "screenshot-tool-result-1",
            role = MessageRole.USER,
            content = listOf(ToolResultContent(
                toolCallId = "screenshot-read-1",
                content = listOf(TextContent("// Screenshot fixture\nval history: List<Message> = emptyList()")),
            )),
            source = ToolMessageSource(callId = "screenshot-read-1"),
        ),
    )

    private fun captureMatrix(slug: String, content: @Composable (CaptureSize) -> Unit) {
        val directory = captureDir()
        captureSizes.forEach { size ->
            captureThemes.forEach { (themeSlug, mode) ->
                val file = File(directory, slug + "-" + themeSlug + "-" + size.width + "x" + size.height + ".png")
                render(file, size) {
                    HarnessTheme(themeMode = mode) {
                        Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas)) {
                            content(size)
                        }
                    }
                }
                val decoded = requireNotNull(ImageIO.read(file)) { "Unreadable PNG: " + file.absolutePath }
                assertEquals(size.width, decoded.width, file.name)
                assertEquals(size.height, decoded.height, file.name)
                assertTrue(file.length() > 0, "Empty PNG: " + file.absolutePath)
            }
        }
    }

    private fun render(file: File, size: CaptureSize, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(
            width = size.width,
            height = size.height,
            density = Density(1f),
            content = content,
        )
        try {
            scene.render()
            val image = scene.render()
            file.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }.bytes)
        } finally {
            scene.close()
        }
    }

    private fun captureDir(): File {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "docs/desktop-ui").isDirectory }
            ?: error("Cannot locate repository docs/desktop-ui from user.dir")
        return File(root, "docs/desktop-ui/captures/live-conversation").apply {
            check(isDirectory || mkdirs()) { "Cannot create capture directory: " + absolutePath }
        }
    }

    private data class CaptureSize(val width: Int, val height: Int)

    private companion object {
        val captureSizes = listOf(CaptureSize(1440, 900), CaptureSize(600, 800))
        val captureThemes = listOf("light" to HarnessThemeMode.Light, "dark" to HarnessThemeMode.Dark)
        val fixtureHost = ConversationHostScope(HostId("screenshot-local-host"), generation = 1, identity = null)
        val fixtureSessionId = SessionId("screenshot-conversation")
        val fixturePanelScope = ThreadPanelScope(fixtureHost.hostId, fixtureSessionId)
        val fixtureModels = listOf(
            ConversationModelChoice("screenshot-provider", "screenshot-model", "Screenshot model"),
            ConversationModelChoice("screenshot-provider", "screenshot-model-2", "Screenshot model 2"),
        )

        fun unexpectedAction(action: String): Nothing = error("Passive screenshot must not dispatch " + action)
    }
}
