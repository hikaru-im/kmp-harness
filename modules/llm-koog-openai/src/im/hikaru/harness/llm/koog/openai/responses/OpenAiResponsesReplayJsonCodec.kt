package im.hikaru.harness.llm.koog.openai.responses

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Strict JSON codec for the versioned OpenAI Responses replay envelope. */
internal object OpenAiResponsesReplayJsonCodec {
    fun encode(state: OpenAiResponsesReplayState): JsonObject =
        buildJsonObject {
            put("kind", state.kind)
            put("version", state.version)
            put("provider", state.provider)
            put("model", state.model)
            put("stopReason", state.stopReason)
            put(
                "blocks",
                buildJsonArray {
                    state.blocks.forEach { block ->
                        add(
                            buildJsonObject {
                                put("index", block.index)
                                put("type", block.type)
                                block.reasoningId?.let { put("reasoningId", it) }
                                put(
                                    "reasoningSummary",
                                    buildJsonArray {
                                        block.reasoningSummary.forEach { value ->
                                            add(JsonPrimitive(value))
                                        }
                                    },
                                )
                                block.reasoningEncrypted?.let {
                                    put("reasoningEncrypted", it)
                                }
                            }
                        )
                    }
                },
            )
        }

    fun decode(raw: JsonElement): OpenAiResponsesReplayState {
        val root =
            raw as? JsonObject
                ?: invalidResponsesReplay("OpenAI Responses replay state must be an object")
        rejectUnknown(root, STATE_KEYS, "state")
        val blocks =
            root["blocks"]?.jsonArray?.map(::decodeBlock)
                ?: invalidResponsesReplay("OpenAI Responses replay state is missing blocks")
        return OpenAiResponsesReplayState(
            kind = root.string("kind"),
            version = root.int("version"),
            provider = root.string("provider"),
            model = root.string("model"),
            stopReason = root.string("stopReason"),
            blocks = blocks,
        )
    }

    private fun decodeBlock(raw: JsonElement): OpenAiResponsesReplayBlock {
        val value =
            raw as? JsonObject
                ?: invalidResponsesReplay("OpenAI Responses replay block must be an object")
        rejectUnknown(value, BLOCK_KEYS, "block")
        val summary =
            value["reasoningSummary"]?.jsonArray?.map { item ->
                item.jsonPrimitive.takeIf { it.isString }?.content
                    ?: invalidResponsesReplay(
                        "OpenAI Responses replay summary must contain strings"
                    )
            } ?: emptyList()
        return OpenAiResponsesReplayBlock(
            index = value.int("index"),
            type = value.string("type"),
            reasoningId = value.optionalString("reasoningId"),
            reasoningSummary = summary,
            reasoningEncrypted = value.optionalString("reasoningEncrypted"),
        )
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: invalidResponsesReplay(
                "OpenAI Responses replay field '$key' must be a string"
            )

    private fun JsonObject.optionalString(key: String): String? =
        this[key]?.let { value ->
            if (value is JsonNull) {
                null
            } else {
                value.jsonPrimitive.takeIf { it.isString }?.content
                    ?: invalidResponsesReplay(
                        "OpenAI Responses replay field '$key' must be a string"
                    )
            }
        }

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull
            ?: invalidResponsesReplay(
                "OpenAI Responses replay field '$key' must be an integer"
            )

    private fun rejectUnknown(
        value: JsonObject,
        allowed: Set<String>,
        label: String,
    ) {
        val unknown = value.keys - allowed
        if (unknown.isNotEmpty()) {
            invalidResponsesReplay(
                "OpenAI Responses replay $label has unknown field(s): ${unknown.joinToString()}"
            )
        }
    }

    private val STATE_KEYS =
        setOf("kind", "version", "provider", "model", "stopReason", "blocks")
    private val BLOCK_KEYS =
        setOf("index", "type", "reasoningId", "reasoningSummary", "reasoningEncrypted")
}
