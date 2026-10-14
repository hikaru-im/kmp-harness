package im.hikaru.harness.client.scheduling

public object ScheduledValidator {
    public fun ruleError(rule: ScheduledRule): String? = when (rule) {
        is ScheduledRule.Hourly -> if (rule.intervalHours !in 1..168 || rule.anchorEpochMillis < 0) "间隔须为 1–168 小时，起点须为有效时间。" else null
        is ScheduledRule.Daily -> wallError(rule.hour, rule.minute, rule.zoneId)
        is ScheduledRule.Weekdays -> wallError(rule.hour, rule.minute, rule.zoneId)
        is ScheduledRule.Weekly -> wallError(rule.hour, rule.minute, rule.zoneId)
    }

    private fun wallError(hour: Int, minute: Int, zoneId: String): String? =
        if (hour !in 0..23 || minute !in 0..59 || zoneId.isBlank()) "时间须为 0–23 时、0–59 分，并指定时区。" else null

    public fun structuralError(task: ScheduledTask): String? = when {
        task.id.isBlank() || task.revision <= 0 -> "任务 ID 或版本无效。"
        task.name.isBlank() || task.name.length > 200 -> "名称须为 1–200 个字符。"
        task.prompt.isBlank() || task.prompt.length > 100_000 -> "提示词须为 1–100000 个字符。"
        !validTarget(task.target) -> "目标目录或会话无效。"
        task.model != null && !validModel(task.model) -> "模型身份无效。"
        task.target is ScheduledTarget.Cron && task.model == null -> "独立任务必须指定真实模型。"
        task.target is ScheduledTarget.Heartbeat && task.model != null -> "会话跟进使用既有会话模型。"
        task.updatedAt != null && task.updatedAt < 0 -> "任务更新时间无效。"
        else -> ruleError(task.schedule)
    }

    public fun snapshotError(snapshot: ScheduledSnapshot): String? = when {
        snapshot.revision < 0 -> "仓库版本无效。"
        snapshot.tasks.map { it.id }.distinct().size != snapshot.tasks.size -> "重复任务 ID。"
        snapshot.deletedTaskIds.any { it.isBlank() } || snapshot.deletedTaskIds.distinct().size != snapshot.deletedTaskIds.size -> "删除记录无效。"
        snapshot.tasks.any { it.id in snapshot.deletedTaskIds || it.revision > snapshot.revision } -> "任务版本或删除记录冲突。"
        else -> snapshot.tasks.firstNotNullOfOrNull(::structuralError) ?: ScheduledLedgerRules.error(snapshot)
    }

    public fun draftErrors(draft: ScheduledDraft, context: ScheduledValidationContext, engine: ScheduleEngine): Map<String, String> = buildMap {
        if (draft.name.trim().isEmpty() || draft.name.trim().length > 200) put("name", "请输入 1–200 个字符的名称。")
        if (draft.prompt.trim().isEmpty() || draft.prompt.length > 100_000) put("prompt", "请输入非空提示词，最多 100000 个字符。")
        if (draft.target == null || draft.target !in context.targets || !validTarget(draft.target)) put("target", "请从当前 Host 选择真实目标。")
        if (draft.target is ScheduledTarget.Cron && (draft.model == null || draft.model !in context.models || !validModel(draft.model))) put("model", "请从当前 Host 选择可用模型。")
        if (draft.target is ScheduledTarget.Heartbeat && draft.model != null) put("model", "会话跟进必须沿用既有会话模型。")
        val rule = draft.schedule.rule()
        if (rule == null) put("schedule", "请输入完整的整数时间或间隔。")
        else engine.validationError(rule)?.let { put("schedule", it) }
    }

    private fun validModel(model: ScheduledModelRef): Boolean = model.providerId.isNotBlank() && model.modelId.isNotBlank()
    private fun validTarget(target: ScheduledTarget): Boolean = when (target) {
        is ScheduledTarget.Heartbeat -> target.sessionId.value.isNotBlank()
        is ScheduledTarget.Cron -> when (val destination = target.destination) {
            ScheduledDestination.Projectless -> true
            is ScheduledDestination.Cwd -> destination.path.startsWith("/") && !destination.path.contains('\u0000') &&
                destination.path.split('/').none { it == ".." || it == "." }
        }
    }
}
