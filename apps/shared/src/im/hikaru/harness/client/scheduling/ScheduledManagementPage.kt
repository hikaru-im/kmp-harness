package im.hikaru.harness.client.scheduling

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.launch

/** Real configuration surface. The page never owns an executor or fabricates service state. */
@Composable
public fun ScheduledManagementPage(
    repository: ScheduledRepository, modifier: Modifier = Modifier,
    query: String? = null, onQueryChange: ((String) -> Unit)? = null,
    onOpenRun: ((SessionId) -> Unit)? = null,
    onRecoverExecution: (() -> Unit)? = null,
    executionRecoveryBusy: Boolean = false,
    executionRecoveryError: String? = null,
    calendar: ScheduleCalendar? = null,
    nowEpochMillis: Long = 0,
) {
    require((query == null) == (onQueryChange == null)) { "Supply query and onQueryChange together" }
    // Each callback owns this render's snapshot, rather than reading a delegated latest state.
    val state = repository.state.collectAsState().value
    val coroutineScope = rememberCoroutineScope()
    var deleteId by remember(repository, state.scope) { mutableStateOf<String?>(null) }
    LaunchedEffect(repository, state.scope, state.context, state.connected) {
        if (state.connected && state.phase == ScheduledLoadPhase.NotLoaded) repository.load()
    }
    val editable = state.connected && state.phase == ScheduledLoadPhase.Ready
    val pageFence = remember(repository, state.scope, state.context, state.connected, state.phase, state.draft, state.mutation, state.selectedTaskId) { Any() }
    val latestFence by rememberUpdatedState(pageFence)
    val latestRepository by rememberUpdatedState(repository)
    fun current(): Boolean {
        val live = repository.state.value
        return latestRepository === repository && latestFence === pageFence && live.scope == state.scope &&
            live.context === state.context && live.connected == state.connected && live.phase == state.phase &&
            live.draft === state.draft && live.mutation == state.mutation && live.selectedTaskId == state.selectedTaskId
    }
    var showLedger by remember(repository, state.scope, state.context, state.connected, state.phase) { mutableStateOf(false) }
    var deleteFence by remember { mutableStateOf<Any?>(null) }
    // The delete confirmation stays open across the delete mutation so it can report progress and
    // failures; unlike `pageFence` it is not keyed on `mutation`, which fences asynchronous callbacks.
    val deleteDialogFence = remember(repository, state.scope, state.context, state.connected, state.phase,
        state.selectedTaskId) { Any() }
    val scroll = key(state.scope, state.draft?.taskId, state.selectedTaskId) { rememberScrollState() }

    Column(modifier.fillMaxSize().semantics { testTag = "scheduled-page" }) {
        ScheduledPageStatus(
            state = state,
            onRetry = { coroutineScope.launch { if (current()) repository.load() } },
            onRecoverExecution = onRecoverExecution?.let { action -> { if (current()) action() } },
            executionRecoveryBusy = executionRecoveryBusy,
            executionRecoveryError = executionRecoveryError,
        )
        ScheduledExecutionModelNotice()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                editable && state.draft != null -> ScheduledPageEditor(
                    state = state,
                    repository = repository,
                    fence = pageFence,
                    scroll = scroll,
                    isCurrent = ::current,
                    onCancel = { if (current()) repository.cancelDraft() },
                    onSave = { coroutineScope.launch { if (current()) repository.save() } },
                )
                editable && state.selectedTask != null -> ScheduledPageDetail(
                    state = state,
                    task = state.selectedTask!!,
                    repository = repository,
                    deleteId = deleteId.takeIf { deleteFence === deleteDialogFence },
                    onDeleteRequest = { if (current()) { deleteId = it; deleteFence = deleteDialogFence } },
                    onDeleteCancel = { if (current()) deleteId = null },
                    onOpenRun = onOpenRun,
                    onTogglePause = { id, paused -> coroutineScope.launch { if (current()) repository.setPaused(id, paused) } },
                    onRunNow = { id -> coroutineScope.launch { if (current()) repository.runNow(id) } },
                    onDelete = { id -> coroutineScope.launch { if (current() && repository.delete(id)) deleteId = null } },
                    scroll = scroll,
                    isCurrent = ::current,
                    calendar = calendar,
                    nowEpochMillis = nowEpochMillis,
                )
                state.phase == ScheduledLoadPhase.Unbound || !state.connected || state.phase == ScheduledLoadPhase.Loading ||
                    state.phase == ScheduledLoadPhase.NotLoaded || state.phase == ScheduledLoadPhase.Error ->
                    ScheduledPageEmptyState(state)
                editable && showLedger -> ScheduledPageLedger(state, repository, onOpenRun, ::current, scroll)
                else -> ScheduledPageLanding(
                    state = state,
                    onTemplate = { name, prompt, frequency ->
                        if (current() && state.canMutate && state.draft == null) {
                            repository.beginCreate()
                            repository.updateDraft { draft -> draft.copy(name = name, prompt = prompt,
                                schedule = draft.schedule.copy(frequency = frequency)) }
                        }
                    },
                    onHistory = { if (current()) showLedger = true }.takeIf { state.effective?.runs?.isNotEmpty() == true },
                )
            }
        }
    }
}

@Composable
private fun ScheduledExecutionModelNotice() {
    HarnessText(
        ScheduledLocalExecutionModel,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { testTag = "scheduled-execution-model" },
        color = HarnessTokens.Colors.TextMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ScheduledPageStatus(
    state: ScheduledManagementState,
    onRetry: () -> Unit,
    onRecoverExecution: (() -> Unit)?,
    executionRecoveryBusy: Boolean,
    executionRecoveryError: String?,
) {
    if (state.phase == ScheduledLoadPhase.Ready && state.connected && state.durabilityWarning == null &&
        state.mutationError == null && state.mutation == null && onRecoverExecution == null &&
        !executionRecoveryBusy && executionRecoveryError == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.durabilityWarning?.let {
            HarnessText(it, Modifier.semantics { testTag = "scheduled-durability-warning" }, color = HarnessTokens.Colors.Danger)
        }
        state.mutationError?.let {
            HarnessText(it, Modifier.semantics { testTag = "scheduled-mutation-error" }, color = HarnessTokens.Colors.Danger)
        }
        when {
            state.phase == ScheduledLoadPhase.Unbound -> HarnessText("尚未选择 Host。", Modifier.semantics { testTag = "scheduled-unbound" })
            !state.connected -> HarnessText("当前 Host 未连接，配置操作不可用。",
                Modifier.semantics { testTag = "scheduled-disconnected" })
            state.phase == ScheduledLoadPhase.NotLoaded || state.phase == ScheduledLoadPhase.Loading -> HarnessText("正在读取任务配置…",
                Modifier.semantics { testTag = "scheduled-loading" })
            state.phase == ScheduledLoadPhase.Error -> {
                HarnessText(state.loadError ?: "读取任务配置失败。", Modifier.semantics { testTag = "scheduled-load-error" },
                    color = HarnessTokens.Colors.Danger)
                HarnessButton("重新读取", onRetry, enabled = state.connected && state.mutation == null,
                    variant = HarnessButtonVariant.Secondary, testTag = "scheduled-page-retry")
            }
            else -> Unit
        }
        if (state.mutation != null) HarnessText("正在保存任务变更…", Modifier.semantics { testTag = "scheduled-saving" })
        if (onRecoverExecution != null || executionRecoveryBusy || executionRecoveryError != null) {
            HarnessButton(if (executionRecoveryBusy) "正在恢复执行服务…" else "恢复执行服务",
                { onRecoverExecution?.invoke() }, enabled = onRecoverExecution != null && !executionRecoveryBusy && state.draft == null && state.mutation == null,
                variant = HarnessButtonVariant.Secondary, testTag = "scheduled-execution-recover")
            executionRecoveryError?.let { HarnessText(it, Modifier.semantics { testTag = "scheduled-execution-recovery-error" },
                color = HarnessTokens.Colors.Danger) }
        }
    }
}

@Composable
private fun ScheduledPageEmptyState(state: ScheduledManagementState) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.phase == ScheduledLoadPhase.Unbound -> HarnessText("选择 Host 后才能读取定时任务。", Modifier.semantics { testTag = "scheduled-unbound-content" })
                !state.connected -> HarnessText("Host 已断开；旧任务和旧历史已隐藏。", Modifier.semantics { testTag = "scheduled-disconnected-content" })
                state.phase == ScheduledLoadPhase.NotLoaded || state.phase == ScheduledLoadPhase.Loading -> HarnessText("任务列表正在读取；完成前不会显示空状态。", Modifier.semantics { testTag = "scheduled-loading-content" })
                state.phase == ScheduledLoadPhase.Error -> HarnessText("任务配置读取失败；请先重新读取。", Modifier.semantics { testTag = "scheduled-error-content" })
            }
        }
    }
}

@Composable
private fun ScheduledPageDetail(
    state: ScheduledManagementState,
    task: ScheduledTask,
    repository: ScheduledRepository,
    deleteId: String?,
    onDeleteRequest: (String) -> Unit,
    onDeleteCancel: () -> Unit,
    onOpenRun: ((SessionId) -> Unit)?,
    onTogglePause: (String, Boolean) -> Unit,
    onRunNow: (String) -> Unit,
    onDelete: (String) -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
    isCurrent: () -> Boolean,
    calendar: ScheduleCalendar?,
    nowEpochMillis: Long,
) {
    val catalogAvailable = task.target in state.context.targets && (task.model == null || task.model in state.context.models)
    val blocked = state.effective?.runs.orEmpty().any { it.task.id == task.id && it.blocksTask }
    val canPause = state.canMutate && (task.status == ScheduledTaskStatus.Active ||
        (state.canActivate && catalogAvailable && !blocked))
    val runs = state.effective?.runs.orEmpty().filter { it.task.id == task.id }.sortedByDescending { it.claimedAt }
    val lastRunLabel = scheduledLastRunLabel(lastRunInstant(runs), nowEpochMillis, calendar)
    Column(Modifier.fillMaxSize().verticalScroll(scroll).semantics { testTag = "scheduled-content-scroll" }
        .padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
    Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().semantics { testTag = "scheduled-detail" },
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HarnessText(task.name, Modifier.semantics { testTag = "scheduled-detail-name" }, style = MaterialTheme.typography.headlineSmall)
        HarnessText("已保存配置 · " + task.status.label(), color = HarnessTokens.Colors.TextMuted)
        scheduledLastUpdatedLabel(task.updatedAt, nowEpochMillis, calendar)?.let { HarnessText(it,
            Modifier.semantics { testTag = "scheduled-detail-updated" }, color = HarnessTokens.Colors.TextMuted) }
        lastRunLabel?.let { HarnessText(it, Modifier.semantics { testTag = "scheduled-detail-last-run" },
            color = HarnessTokens.Colors.TextMuted) }
        HarnessText(task.prompt, Modifier.semantics { testTag = "scheduled-detail-prompt" })
        ScheduledPageValue(task.target.label(), "scheduled-detail-target")
        ScheduledPageValue(task.schedule.label(), "scheduled-detail-schedule")
        task.model?.let { ScheduledPageValue(it.label(), "scheduled-detail-model") }
        HarnessText("配置版本 " + task.revision, color = HarnessTokens.Colors.TextMuted)
        if (!catalogAvailable) HarnessText("目标或模型不在当前 Host 的真实可用目录中。", color = HarnessTokens.Colors.TextMuted,
            modifier = Modifier.semantics { testTag = "scheduled-catalog-unavailable" })
        if (blocked) HarnessText("任务有在途或 Unknown 运行；不能启用或重新投递。",
            Modifier.semantics { testTag = "scheduled-run-blocked" }, color = HarnessTokens.Colors.TextMuted)
        if (blocked) {
            val blockingRuns = state.effective?.runs.orEmpty().filter { it.task.id == task.id && it.blocksTask }
            val unknownRuns = blockingRuns.count { it.phase == ScheduledRunPhase.Unknown }
            val inFlightRuns = blockingRuns.size - unknownRuns
            HarnessText(
                "阻塞中的运行：进行中 " + inFlightRuns + " 条，结果未知 " + unknownRuns +
                    " 条。结果未知的运行没有精确终态证据，不会自动重新投递；请先打开对应会话人工核对，" +
                    "在证据确认前不能启用或立即运行。",
                Modifier.semantics { testTag = "scheduled-blocked-runs" }, color = HarnessTokens.Colors.TextMuted)
        }
        state.executionUnavailableReason?.let { HarnessText(it, Modifier.semantics { testTag = "scheduled-execution-unavailable" }, color = HarnessTokens.Colors.TextMuted) }
        if (task.target is ScheduledTarget.Heartbeat) HarnessText(
            "会话跟进会投递到此任务指向的既有会话，并使用该会话自身的模型。",
            Modifier.semantics { testTag = "scheduled-heartbeat-target" }, color = HarnessTokens.Colors.TextMuted)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("编辑", { if (isCurrent()) repository.beginEdit(task.id) }, enabled = state.canMutate, testTag = "scheduled-edit")
            HarnessButton(if (task.status == ScheduledTaskStatus.Active) "暂停" else "启用",
                { onTogglePause(task.id, task.status == ScheduledTaskStatus.Active) }, enabled = canPause,
                variant = HarnessButtonVariant.Secondary, testTag = "scheduled-pause")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("立即运行", { onRunNow(task.id) }, enabled = state.canRunNow && catalogAvailable,
                variant = HarnessButtonVariant.Secondary, testTag = "scheduled-run-now")
            HarnessButton("删除", { onDeleteRequest(task.id) }, enabled = state.canMutate,
                variant = HarnessButtonVariant.Destructive, testTag = "scheduled-delete")
        }
        }
        HarnessConfirmDialog(
            visible = deleteId == task.id,
            onDismissRequest = onDeleteCancel,
            title = "删除 " + task.name + "？",
            description = "这将永久删除定时任务，并停止日后的运行；已开始的任务不会被取消，" +
                "会话历史也不会被清理。",
            cancelLabel = "取消",
            confirmLabel = "删除定时任务",
            submitState = when {
                state.mutation == ScheduledMutation.Delete -> HarnessConfirmSubmitState.Submitting
                state.mutationError != null -> HarnessConfirmSubmitState.Failed(state.mutationError!!)
                else -> HarnessConfirmSubmitState.Ready
            },
            onCancel = onDeleteCancel,
            onConfirm = { onDelete(task.id) },
            intent = HarnessConfirmIntent.Destructive,
            testTag = "scheduled-delete-confirmation",
        )
        ScheduledPageRunRows(state, repository, runs, onOpenRun, isCurrent)
    }
    }
}

@Composable
private fun ScheduledPageLedger(state: ScheduledManagementState, repository: ScheduledRepository,
    onOpenRun: ((SessionId) -> Unit)?, isCurrent: () -> Boolean, scroll: androidx.compose.foundation.ScrollState) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp)
        .semantics { testTag = "scheduled-content-scroll" }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScheduledPageRunRows(state, repository, state.effective?.runs.orEmpty().sortedByDescending { it.claimedAt }, onOpenRun, isCurrent)
    }
}

@Composable
private fun ScheduledPageRunRows(state: ScheduledManagementState, repository: ScheduledRepository, runs: List<ScheduledRun>,
    onOpenRun: ((SessionId) -> Unit)?, isCurrent: () -> Boolean) {
        HarnessText("运行历史", Modifier.semantics { testTag = "scheduled-page-history" }, style = MaterialTheme.typography.titleMedium)
        if (runs.isEmpty()) HarnessText("真实账本尚无运行记录。", Modifier.semantics { testTag = "scheduled-page-history-empty" })
        runs.forEach { run ->
            HarnessSurface(Modifier.fillMaxWidth().semantics { testTag = "scheduled-page-run:" + run.id }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HarnessText(run.task.name + " · " + run.phase.label())
                    HarnessText("任务版本 " + run.task.revision + " · " + kotlin.time.Instant.fromEpochMilliseconds(run.claimedAt))
                    run.detail?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted) }
                    run.terminal?.let { HarnessText(it.reason) }
                    val session = repository.runSessionRef(run.id)
                    HarnessButton("打开运行会话", { if (isCurrent()) repository.runSessionRef(run.id)?.let { onOpenRun?.invoke(it) } },
                        enabled = session != null && onOpenRun != null && state.connected,
                        variant = HarnessButtonVariant.Secondary, testTag = "scheduled-page-open-run:" + run.id)
                    if (session == null) HarnessText("此运行尚无已确认的会话引用。", color = HarnessTokens.Colors.TextMuted)
                    else if (onOpenRun == null) HarnessText("宿主尚未接入运行会话导航。", color = HarnessTokens.Colors.TextMuted)
                }
            }
        }
 }

@Composable
private fun ScheduledPageEditor(
    state: ScheduledManagementState,
    repository: ScheduledRepository,
    fence: Any,
    scroll: androidx.compose.foundation.ScrollState,
    isCurrent: () -> Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = state.draft ?: return
    val errors = repository.draftErrors() + state.validationErrors
    val canEdit = state.connected && state.phase == ScheduledLoadPhase.Ready && state.mutation == null
    val targets = state.context.targets
    val models = state.context.models
    Column(Modifier.fillMaxSize().verticalScroll(scroll).semantics { testTag = "scheduled-content-scroll" }
        .padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
    Column(Modifier.widthIn(max = 820.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HarnessText(if (draft.expectedTaskRevision == null) "创建暂停任务" else "编辑任务", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { testTag = "scheduled-editor" })
        HarnessText("保存后仍为暂停状态；保存不会启动执行。", color = HarnessTokens.Colors.TextMuted)
        if (targets.isEmpty()) HarnessText("当前 Host 未提供可验证的目录或既有会话目标。", Modifier.semantics { testTag = "scheduled-targets-unavailable" })
        if (draft.target is ScheduledTarget.Heartbeat) HarnessText(
            "会话跟进会把提示词投递到所选既有会话；不创建新会话，也不单独指定模型。",
            Modifier.semantics { testTag = "scheduled-heartbeat-target" }, color = HarnessTokens.Colors.TextMuted)
        HarnessTextField(draft.name, { value -> if (isCurrent()) repository.updateDraft { it.copy(name = value) } }, "名称", Modifier.fillMaxWidth(),
            enabled = canEdit, supportingText = errors["name"], isError = errors["name"] != null, testTag = "scheduled-name")
        HarnessTextField(draft.prompt, { value -> if (isCurrent()) repository.updateDraft { it.copy(prompt = value) } }, "提示词",
            Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp), enabled = canEdit, singleLine = false,
            supportingText = errors["prompt"], isError = errors["prompt"] != null, testTag = "scheduled-prompt")
        ScheduledPagePicker(draft.target, targets, { it?.label() ?: "选择真实目标" }, canEdit && targets.isNotEmpty(), "scheduled-target", fence) { target ->
            if (isCurrent()) repository.updateDraft { it.copy(target = target, model = if (target is ScheduledTarget.Heartbeat) null else it.model) }
        }
        errors["target"]?.let { HarnessText(it, Modifier.semantics { testTag = "scheduled-target-error" }, color = HarnessTokens.Colors.Danger) }
        if (draft.target is ScheduledTarget.Cron) {
            ScheduledPagePicker(draft.model, models, { it?.label() ?: "选择真实模型" }, canEdit && models.isNotEmpty(), "scheduled-model", fence) { model ->
                if (isCurrent()) repository.updateDraft { it.copy(model = model) }
            }
            if (models.isEmpty()) HarnessText("当前 Host 没有可用模型，无法保存独立任务。", color = HarnessTokens.Colors.TextMuted)
            errors["model"]?.let { HarnessText(it, Modifier.semantics { testTag = "scheduled-model-error" }, color = HarnessTokens.Colors.Danger) }
        }
        ScheduledPagePicker(draft.schedule.frequency, ScheduledFrequency.entries.toList(), { it.label() }, canEdit,
            "scheduled-frequency", fence) { frequency -> if (isCurrent()) repository.updateDraft { it.copy(schedule = it.schedule.copy(frequency = frequency)) } }
        if (draft.schedule.frequency == ScheduledFrequency.Hourly) {
            HarnessTextField(draft.schedule.intervalHours, { value -> if (isCurrent()) repository.updateDraft { it.copy(schedule = it.schedule.copy(intervalHours = value)) } },
                "间隔小时", Modifier.fillMaxWidth(), enabled = canEdit, testTag = "scheduled-interval")
        } else {
            HarnessTextField(draft.schedule.hour, { value -> if (isCurrent()) repository.updateDraft { it.copy(schedule = it.schedule.copy(hour = value)) } },
                "小时（0–23）", Modifier.fillMaxWidth(), enabled = canEdit, testTag = "scheduled-hour")
            HarnessTextField(draft.schedule.minute, { value -> if (isCurrent()) repository.updateDraft { it.copy(schedule = it.schedule.copy(minute = value)) } },
                "分钟（0–59）", Modifier.fillMaxWidth(), enabled = canEdit, testTag = "scheduled-minute")
            HarnessTextField(draft.schedule.zoneId, { value -> if (isCurrent()) repository.updateDraft { it.copy(schedule = it.schedule.copy(zoneId = value)) } },
                "时区（例如 Asia/Shanghai）", Modifier.fillMaxWidth(), enabled = canEdit, testTag = "scheduled-zone")
            if (draft.schedule.frequency == ScheduledFrequency.Weekly) ScheduledPagePicker(draft.schedule.day,
                ScheduledWeekday.entries.toList(), { it.label() }, canEdit, "scheduled-weekday", fence) { day ->
                if (isCurrent()) repository.updateDraft { it.copy(schedule = it.schedule.copy(day = day)) }
            }
        }
        errors["schedule"]?.let { HarnessText(it, Modifier.semantics { testTag = "scheduled-schedule-error" }, color = HarnessTokens.Colors.Danger) }
        val preview = remember(repository, draft.schedule) { runCatching { repository.preview(draft, kotlin.time.Clock.System.now().toEpochMilliseconds()) }.getOrNull() }
        preview?.let { HarnessText("规则预览：" + kotlin.time.Instant.fromEpochMilliseconds(it) + "（暂停任务不会执行）",
            Modifier.semantics { testTag = "scheduled-rule-preview" }, color = HarnessTokens.Colors.TextMuted) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("保存为暂停任务", onSave, enabled = state.canMutate && errors.isEmpty(), testTag = "scheduled-save")
            HarnessButton("放弃草稿", onCancel, enabled = state.mutation == null, variant = HarnessButtonVariant.Secondary,
                testTag = "scheduled-cancel")
        }
    }
}

}

internal fun ScheduledTaskStatus.label(): String = when (this) { ScheduledTaskStatus.Paused -> "已暂停"; ScheduledTaskStatus.Active -> "已启用" }
internal fun ScheduledRunPhase.label(): String = when (this) {
    ScheduledRunPhase.Claimed -> "已认领"; ScheduledRunPhase.CreatingSession -> "正在创建会话"; ScheduledRunPhase.SessionCreated -> "会话已创建"; ScheduledRunPhase.Dispatching -> "正在投递"; ScheduledRunPhase.Accepted -> "已接收"; ScheduledRunPhase.Succeeded -> "已成功"; ScheduledRunPhase.Failed -> "已失败"; ScheduledRunPhase.Blocked -> "受阻"; ScheduledRunPhase.Incomplete -> "未完成"; ScheduledRunPhase.Interrupted -> "已中断"; ScheduledRunPhase.Aborted -> "已终止"; ScheduledRunPhase.Rejected -> "已拒绝"; ScheduledRunPhase.Unknown -> "结果未知"; ScheduledRunPhase.Withdrawn -> "已安全撤回"
}
internal fun ScheduledModelRef.label(): String = providerId + " / " + modelId
internal fun ScheduledTarget.label(): String = when (this) {
    is ScheduledTarget.Heartbeat -> "跟进会话 " + sessionId.value
    is ScheduledTarget.Cron -> when (val d = destination) { ScheduledDestination.Projectless -> "独立任务 · 无项目"; is ScheduledDestination.Cwd -> "独立任务 · " + d.path }
}
internal fun ScheduledFrequency.label(): String = when (this) { ScheduledFrequency.Hourly -> "每小时"; ScheduledFrequency.Daily -> "每天"; ScheduledFrequency.Weekdays -> "工作日"; ScheduledFrequency.Weekly -> "每周" }
internal fun ScheduledWeekday.label(): String = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[ordinal]
internal fun ScheduledRule.label(): String = when (this) {
    is ScheduledRule.Hourly -> "每 " + intervalHours + " 小时（固定时长）"
    is ScheduledRule.Daily -> "每天 " + hour + ":" + minute.toString().padStart(2, '0') + " · " + zoneId
    is ScheduledRule.Weekdays -> "工作日 " + hour + ":" + minute.toString().padStart(2, '0') + " · " + zoneId
    is ScheduledRule.Weekly -> day.label() + " " + hour + ":" + minute.toString().padStart(2, '0') + " · " + zoneId
}
