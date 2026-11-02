package im.hikaru.harness.client.settings

import im.hikaru.harness.client.connection.ReasoningOptions

/** Client preflight mirrors the finite Host contract; the Host remains authoritative. */
public fun reasoningOptionsError(
    options: ReasoningOptions,
    capabilities: ModelReasoningCapabilities,
    maxTokens: Long? = capabilities.defaultMaxTokens,
): String? {
    if (options.mode != "provider-default") {
        supportError(capabilities.modeSupport, "思考模式")?.let { return it }
        if (options.mode !in capabilities.modes) return "当前模型不支持所选思考模式"
    }
    options.effort?.let { effort ->
        supportError(capabilities.effortSupport, "强度")?.let { return it }
        if (effort.value !in capabilities.efforts) return "当前模型不支持所选强度"
    }
    options.budgetTokens?.let { budget ->
        supportError(capabilities.budgetSupport, "思考预算")?.let { return it }
        if (capabilities.minBudget != null && budget < capabilities.minBudget) return "预算低于模型允许范围"
        if (capabilities.maxBudget != null && budget > capabilities.maxBudget) return "预算超过模型允许范围"
    }
    options.executionModeId?.let { execution ->
        supportError(capabilities.executionModeSupport, "执行模式")?.let { return it }
        if (execution !in capabilities.executionModes) return "当前模型不支持所选执行模式"
    }
    capabilities.constraints.forEach { constraint ->
        when (constraint) {
            ModelReasoningConstraint.EffortBudgetExclusive ->
                if (options.effort != null && options.budgetTokens != null) return "当前模型的强度与预算不能同时设置"
            ModelReasoningConstraint.BudgetLessThanMaxTokens ->
                if (options.budgetTokens != null && (maxTokens == null || options.budgetTokens >= maxTokens)) {
                    return "当前模型要求输出上限大于思考预算，请先调整模型的输出配置"
                }
            is ModelReasoningConstraint.ForMode -> if (options.mode == constraint.mode) {
                options.effort?.let { effort ->
                    constraint.effortSupport?.let { supportError(it, "此模式下的强度") }?.let { return it }
                    if (constraint.allowedEfforts != null && effort.value !in constraint.allowedEfforts) return "所选模式不允许此强度"
                }
                if (options.budgetTokens != null) {
                    constraint.budgetSupport?.let { supportError(it, "此模式下的预算") }?.let { return it }
                } else if (constraint.requiresBudget) return "所选思考模式需要填写预算"
            }
        }
    }
    return null
}

private fun supportError(support: ModelCapabilitySupport, label: String): String? = when (support) {
    ModelCapabilitySupport.SUPPORTED -> null
    ModelCapabilitySupport.UNSUPPORTED -> "当前模型不支持$label"
    ModelCapabilitySupport.UNKNOWN -> "当前模型的${label}能力尚未确认，请跟随厂商默认"
}
