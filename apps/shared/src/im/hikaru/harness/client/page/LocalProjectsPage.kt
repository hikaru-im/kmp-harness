package im.hikaru.harness.client.page

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.SessionSummary
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.ui.foundation.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public fun LocalProjectFailure.message(): String = when (this) {
    is LocalProjectFailure.Unavailable -> message
    is LocalProjectFailure.Error -> message
    is LocalProjectFailure.AccessDenied -> message
    is LocalProjectFailure.UnsupportedVersion -> "项目文件版本 $version 暂不支持。"
    is LocalProjectFailure.Conflict -> message
    is LocalProjectFailure.Rejected -> message
    LocalProjectFailure.StaleBinding -> "项目绑定已变化，请重新选择当前 Host 的项目。"
}

/** Compare the observed state's binding before the caller's asynchronous bind effect runs. */
public fun localProjectsForBinding(state: LocalProjectsState, currentBinding: LocalProjectBinding?): LocalProjectsState =
    if (currentBinding != null && state.binding?.sameOwner(currentBinding) == true) state
    else LocalProjectsState(binding = currentBinding)

@Composable
public fun LocalProjectsPage(
    state: LocalProjectsState, query: String, onQueryChange: (String) -> Unit,
    onCreate: () -> Unit, onRefresh: () -> Unit, onOpen: (LocalProjectId) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .semantics { testTag = "local-projects-page" }.padding(HarnessTokens.Spacing.Four), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectsRecentHeader("local-project-header", "本机项目", "将已有目录登记为项目，在该目录中开始会话。") {
            HarnessButton("登记已有目录", onCreate, enabled = state.canRefresh && state.phase in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty) && state.mutation == null, testTag = "local-project-create")
            HarnessButton("刷新", onRefresh, enabled = state.canRefresh && state.phase != LocalProjectsPhase.Loading,
                variant = HarnessButtonVariant.Secondary, testTag = "local-project-refresh")
        }
        HarnessSearchField(query, onQueryChange, "搜索名称或目录", "搜索项目", Modifier.fillMaxWidth(),
            enabled = state.phase in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty), testTag = "local-project-search")
        LocalProjectStatus(state)
        val visible = state.projects.filter { query.isBlank() || it.name.contains(query, true) || it.roots.any { root -> root.path.contains(query, true) } }
        if (state.phase == LocalProjectsPhase.Empty) HarnessText("暂无本机项目。", testModifier("local-projects-empty"), color = HarnessTokens.Colors.TextMuted)
        else if (state.phase == LocalProjectsPhase.Ready && visible.isEmpty()) HarnessText("没有匹配的项目。", testModifier("local-projects-no-results"), color = HarnessTokens.Colors.TextMuted)
        visible.forEach { project ->
            HarnessRow(project.name, description = project.roots.single().path, onClick = { onOpen(project.id) },
                testTag = "local-project-row-${project.id.value}", titleMaxLines = 1, descriptionMaxLines = 1)
        }
    }
}

@Composable
public fun LocalProjectDetailPage(
    state: LocalProjectsState, id: LocalProjectId, liveSessions: List<SessionSummary>,
    onBindDirectory: (LocalProject) -> Unit, onStart: (LocalProjectId) -> Unit, onOpenSession: (SessionSummary) -> Unit,
    onRefresh: () -> Unit,
) {
    val latestState by rememberUpdatedState(state)
    val latestSessions by rememberUpdatedState(liveSessions)
    val latestStart by rememberUpdatedState(onStart)
    val latestBind by rememberUpdatedState(onBindDirectory)
    val latestOpen by rememberUpdatedState(onOpenSession)
    val capturedBinding = state.binding
    val active = remember(state.binding, id) { booleanArrayOf(true) }
    DisposableEffect(active) { onDispose { active[0] = false } }
    fun current(project: LocalProject): Boolean = active[0] && latestState.binding == capturedBinding &&
        latestState.phase in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty) &&
        latestState.canRefresh && latestState.mutation == null && latestState.projects.firstOrNull { it.id == id } == project
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .semantics { testTag = "local-project-detail" }.padding(HarnessTokens.Spacing.Four), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LocalProjectStatus(state)
        val project = state.projects.firstOrNull { it.id == id }
        if (project == null) {
            HarnessText(if (state.phase == LocalProjectsPhase.Loading) "正在读取项目…" else "此项目在当前 Host 中不可用。",
                testModifier("local-project-detail-unavailable"))
            HarnessButton("刷新", onRefresh, enabled = state.canRefresh)
        } else {
            HarnessText(project.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
            ProjectsRecentValue("项目名称", project.name, "local-project-name")
            ProjectsRecentValue("项目目录", project.roots.single().path, "local-project-root")
            ProjectsRecentActions("local-project-detail-actions", stackBelow = 320.dp) {
                HarnessButton("在此项目开始会话", { if (current(project)) latestStart(id) }, enabled = current(project), testTag = "local-project-start")
                HarnessButton("更换目录", { if (current(project)) latestBind(project) }, enabled = current(project),
                    variant = HarnessButtonVariant.Secondary, testTag = "local-project-bind")
            }
            HarnessText("关联会话", style = MaterialTheme.typography.titleMedium)
            val memberships = state.snapshot?.memberships.orEmpty().filter { it.projectId == id }
            if (memberships.isEmpty()) HarnessText("暂无关联会话。", testModifier("local-project-memberships-empty"), color = HarnessTokens.Colors.TextMuted)
            else if (memberships.any { membership -> liveSessions.none { it.id == membership.session.id } })
                HarnessText("部分关联会话未载入，当前服务不能恢复这些会话。", testModifier("local-project-memberships-not-live"), color = HarnessTokens.Colors.TextMuted)
            memberships.forEach { membership ->
                val live = liveSessions.firstOrNull { it.id == membership.session.id }
                HarnessRow(membership.session.id.value,
                    description = if (live == null) "未载入 · ${membership.session.cwd.orEmpty()}" else live.cwd,
                    onClick = live?.let { session -> {
                        if (current(project) && latestState.snapshot?.memberships?.contains(membership) == true &&
                            latestSessions.any { it == session }) latestOpen(session)
                    } }, testTag = "local-project-session-${membership.session.id.value}",
                    trailing = { ProjectsRecentCopy(membership.session.id.value, "复制会话 ID", "local-project-session-copy-${membership.session.id.value}") })
            }
        }
    }
}

@Composable
public fun LocalProjectPicker(
    state: LocalProjectsState, onSelect: (LocalProjectId?) -> Unit, onManage: () -> Unit, enabled: Boolean,
    currentBinding: LocalProjectBinding? = state.binding,
) {
    val visibleState = localProjectsForBinding(state, currentBinding)
    val latestState by rememberUpdatedState(visibleState)
    val latestBinding by rememberUpdatedState(currentBinding)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestManage by rememberUpdatedState(onManage)
    // Each binding owns its popup and callbacks, including a selection queued before recomposition.
    key(currentBinding) {
        var expanded by remember { mutableStateOf(false) }
        val active = remember { booleanArrayOf(true) }
        DisposableEffect(active) { onDispose { active[0] = false } }
        val capturedBinding = currentBinding
        fun current(): Boolean = active[0] && capturedBinding != null && latestBinding?.sameOwner(capturedBinding) == true &&
            latestState.binding?.sameOwner(capturedBinding) == true && latestEnabled && latestState.mutation == null &&
            latestState.phase in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty)
        val loaded = visibleState.phase in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty)
        Column(Modifier.fillMaxWidth().semantics { testTag = "local-project-picker" }) {
            ProjectsRecentActions("local-project-picker-actions") {
                val projectsByKey = visibleState.projects.associateBy { "project:${it.id.value}" }
                val entries = listOf(HarnessMenuEntry.Item("default-directory", "默认工作目录",
                    selected = visibleState.selectedProjectId == null, testTag = "默认工作目录")) +
                    projectsByKey.map { (key, project) -> HarnessMenuEntry.Item(key, project.name,
                        selected = visibleState.selectedProjectId == project.id,
                        testTag = "local-project-picker-item-${project.id.value}") }
                HarnessMenu(
                    expanded = expanded && current(), entries = entries, enabled = current(), selectionScope = capturedBinding,
                    onExpandedChange = { if (!it || current()) expanded = it },
                    onSelect = { key ->
                        if (current()) {
                            if (key == "default-directory") latestSelect(null)
                            else projectsByKey[key]?.let { project ->
                                if (latestState.projects.any { it.id == project.id }) latestSelect(project.id)
                            }
                        }
                    },
                    modifier = Modifier.widthIn(max = 280.dp),
                    popupModifier = Modifier.semantics { testTag = "local-project-picker-menu" },
                    popupMaxHeight = 320.dp,
                    wrapLabels = true,
                    anchor = { toggle ->
                        HarnessButton(visibleState.selectedProject?.name ?: "默认工作目录", { if (current()) toggle() },
                            modifier = Modifier.widthIn(max = 280.dp), enabled = current(),
                            variant = HarnessButtonVariant.Secondary, testTag = "local-project-picker-open")
                    },
                )
                HarnessButton("管理项目", { if (active[0] && latestBinding == capturedBinding) latestManage() },
                    variant = HarnessButtonVariant.Ghost, testTag = "local-project-manage")
            }
            if (!loaded) LocalProjectStatus(visibleState)
            visibleState.selectedProject?.let {
                ProjectsRecentValue("工作目录", it.roots.single().path, "local-project-picker-root")
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
public fun LocalProjectEditorDialog(
    state: LocalProjectsState, project: LocalProject?, onDismiss: () -> Unit,
    onCreate: (CreateLocalProject) -> Unit, onBind: (LocalProjectId, String) -> Unit, onRetryCreate: () -> Unit,
) {
    val pending = state.pendingCreate.takeIf { project == null }
    var name by remember(state.binding, project?.id, pending?.requestId) { mutableStateOf(pending?.name ?: project?.name.orEmpty()) }
    var directory by remember(state.binding, project?.id, pending?.requestId) { mutableStateOf(pending?.directory ?: project?.roots?.single()?.path.orEmpty()) }
    val busy = state.mutation != null
    val latestState by rememberUpdatedState(state)
    val capturedBinding = state.binding
    val active = remember(state.binding, project?.id, pending?.requestId) { booleanArrayOf(true) }
    DisposableEffect(active) { onDispose { active[0] = false } }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestCreate by rememberUpdatedState(onCreate)
    val latestBind by rememberUpdatedState(onBind)
    val latestRetry by rememberUpdatedState(onRetryCreate)
    fun current(): Boolean = active[0] && latestState.binding == capturedBinding && latestState.canRefresh && latestState.mutation == null &&
        (project == null || latestState.projects.firstOrNull { it.id == project.id } == project)
    val dismiss = { if (active[0] && latestState.binding == capturedBinding) latestDismiss() }
    HarnessDialog(true, dismiss, if (project == null) "登记本机项目" else "更换项目目录") {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HarnessText("输入已有目录的绝对路径。", color = HarnessTokens.Colors.TextMuted)
            if (project == null) HarnessTextField(name, { name = it }, "项目名称", Modifier.fillMaxWidth(),
                enabled = !busy && pending == null, testTag = "local-project-name-input")
            HarnessTextField(directory, { directory = it }, "已有目录", Modifier.fillMaxWidth(),
                enabled = !busy && pending == null, testTag = "local-project-directory-input")
            state.mutationFailure?.let { HarnessText(it.message(), testModifier("local-project-editor-error"), color = HarnessTokens.Colors.Danger) }
            pending?.let { HarnessText("有待确认的创建请求，重试会使用原请求。", color = HarnessTokens.Colors.TextMuted) }
            ProjectsRecentActions("local-project-editor-actions") {
                HarnessButton(if (pending != null) "重试确认" else "保存", {
                    if (current() && latestState.pendingCreate == state.pendingCreate) when {
                        pending != null -> latestRetry()
                        project != null -> latestBind(project.id, directory)
                        else -> latestCreate(CreateLocalProject(Uuid.random().toString(), name, directory))
                    }
                }, enabled = current() && directory.isNotBlank() && (project != null || name.isNotBlank()), testTag = "local-project-editor-save")
                HarnessButton("关闭", dismiss, variant = HarnessButtonVariant.Secondary, testTag = "local-project-editor-close")
            }
        }
    }
}

@Composable
public fun LocalProjectStatus(state: LocalProjectsState) {
    val label = when (state.phase) {
        LocalProjectsPhase.Unbound -> "尚未绑定可用的本机 Host。"
        LocalProjectsPhase.Loading -> "正在读取本机项目…"
        LocalProjectsPhase.Empty -> "项目读取成功，尚未登记目录。"
        LocalProjectsPhase.Ready -> "已登记 ${state.projects.size} 个本机项目。"
        LocalProjectsPhase.Error -> "本机项目读取失败。"
        LocalProjectsPhase.Unavailable -> "本机项目服务不可用。"
        LocalProjectsPhase.Unsupported -> "本机项目文件版本不支持。"
        LocalProjectsPhase.AccessDenied -> "没有权限读取本机项目。"
    }
    HarnessText(label, Modifier.semantics {
        testTag = "local-project-status-${state.phase.name.lowercase()}"; stateDescription = state.phase.name
    }, color = HarnessTokens.Colors.TextMuted)
    state.failure?.let { HarnessText(it.message(), testModifier("local-project-load-error"), color = HarnessTokens.Colors.Danger) }
    state.mutationFailure?.let { HarnessText(it.message(), testModifier("local-project-mutation-error"), color = HarnessTokens.Colors.Danger) }
    state.durabilityWarning?.let { HarnessText(it, color = HarnessTokens.Colors.Danger) }
}

private fun testModifier(tag: String): Modifier = Modifier.semantics { testTag = tag }
