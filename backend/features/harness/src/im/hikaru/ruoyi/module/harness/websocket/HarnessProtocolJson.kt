package im.hikaru.ruoyi.module.harness.websocket

import kotlinx.serialization.json.Json

/** Harness KMP wire contract 的统一 JSON 配置。 */
internal val HarnessProtocolJson =
    Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
