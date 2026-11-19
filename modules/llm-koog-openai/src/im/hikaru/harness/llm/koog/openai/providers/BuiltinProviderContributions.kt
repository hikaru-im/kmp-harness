package im.hikaru.harness.llm.koog.openai.providers

import im.hikaru.harness.llm.*

/** Official endpoint profiles, reviewed against vendor documentation on 2026-10-21. */
public object BuiltinProviderContributions {
    public const val CHAT: String = "openai-chat-completions"
    public const val RESPONSES: String = "openai-responses"
    public const val MESSAGES: String = "anthropic-messages"
    public const val GENERATE_CONTENT: String = "gemini-generate-content"

    public fun all(): List<LlmProviderContribution> = descriptors().map { descriptor ->
        LlmProviderContribution(
            descriptor = descriptor,
            discovery = OfficialProviderModelDiscovery(descriptor.id),
            capabilityRules = if (descriptor.id in setOf("openai", "deepseek", "alibaba", "moonshot", "zhipu"))
                OpenAiCompatibleCapabilityRules else null,
        )
    }

    public fun descriptors(): List<LlmProviderDescriptor> = listOf(
        descriptor("openai", "OpenAI", listOf(CHAT, RESPONSES), listOf(
            profile("standard", "OpenAI", "https://api.openai.com/v1", RESPONSES),
        )),
        descriptor("anthropic", "Claude", listOf(MESSAGES), listOf(
            profile("standard", "Claude API", "https://api.anthropic.com", MESSAGES),
        ), listOf(LlmConnectionField("workspace", "Workspace ID（仅多 workspace 凭据需要）"))),
        descriptor("google", "Google Gemini", listOf(GENERATE_CONTENT), listOf(
            profile("developer-api", "Gemini Developer API", "https://generativelanguage.googleapis.com/v1beta", GENERATE_CONTENT),
        )),
        descriptor("deepseek", "DeepSeek", listOf(CHAT, RESPONSES), listOf(
            profile("standard", "DeepSeek API", "https://api.deepseek.com", CHAT),
        )),
        descriptor("alibaba", "阿里云百炼 / Qwen", listOf(CHAT, RESPONSES), listOf(
            profile("singapore", "新加坡", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", CHAT, "ap-southeast-1"),
            profile("beijing", "中国（北京）", "https://{workspace}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1", CHAT, "cn-beijing", workspace = true),
            profile("hong-kong", "中国（香港）", "https://cn-hongkong.dashscope.aliyuncs.com/compatible-mode/v1", CHAT, "cn-hongkong"),
            profile("frankfurt", "德国（法兰克福）", "https://{workspace}.eu-central-1.maas.aliyuncs.com/compatible-mode/v1", CHAT, "eu-central-1", workspace = true),
            profile("tokyo", "日本（东京）", "https://{workspace}.ap-northeast-1.maas.aliyuncs.com/compatible-mode/v1", CHAT, "ap-northeast-1", workspace = true),
            profile("virginia", "美国（弗吉尼亚）", "https://{workspace}.us-east-1.maas.aliyuncs.com/compatible-mode/v1", CHAT, "us-east-1", workspace = true),
        ), listOf(LlmConnectionField("region", "地区", required = true), LlmConnectionField("workspace", "Workspace ID（所选入口需要时）"))),
        descriptor("moonshot", "Kimi / Moonshot", listOf(CHAT), listOf(
            profile("china", "中国", "https://api.moonshot.cn/v1", CHAT, "china"),
            profile("international", "国际", "https://api.moonshot.ai/v1", CHAT, "international"),
        ), listOf(LlmConnectionField("region", "中国 / 国际", required = true))),
        descriptor("zhipu", "智谱 GLM / Z.ai", listOf(CHAT), listOf(
            profile("china-standard", "中国 · 标准 API", "https://open.bigmodel.cn/api/paas/v4", CHAT, "china"),
            profile("international-standard", "国际 · 标准 API", "https://api.z.ai/api/paas/v4", CHAT, "international"),
            profile("china-coding", "中国 · Coding Plan", "https://open.bigmodel.cn/api/coding/paas/v4", CHAT, "china", "coding-plan"),
            profile("international-coding", "国际 · Coding Plan", "https://api.z.ai/api/coding/paas/v4", CHAT, "international", "coding-plan"),
        ), listOf(LlmConnectionField("region", "中国 / 国际", required = true), LlmConnectionField("serviceVariant", "标准 API / Coding Plan", required = true)), online = false),
    )

    private fun profile(
        id: String, label: String, base: String, api: String,
        region: String? = null, service: String = "standard", workspace: Boolean = false,
    ): LlmEndpointProfile = LlmEndpointProfile(id, label, base, api, region, service, workspace)

    private fun descriptor(
        id: String, name: String, protocols: List<String>, profiles: List<LlmEndpointProfile>,
        fields: List<LlmConnectionField> = emptyList(), online: Boolean = true,
    ): LlmProviderDescriptor = LlmProviderDescriptor(
        id = id, displayName = name, protocols = protocols,
        defaultEndpoints = profiles.associate { it.id to it.baseUrl },
        supportsDiscovery = online, endpointProfiles = profiles,
        connectionFields = fields + LlmConnectionField("apiKey", "API key", required = true, secret = true),
        discoveryStatus = if (online) "documented" else "unverified",
    )
}
