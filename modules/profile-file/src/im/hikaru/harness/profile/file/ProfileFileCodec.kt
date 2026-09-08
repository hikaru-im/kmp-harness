package im.hikaru.harness.profile.file

import im.hikaru.harness.loader.Entry
import im.hikaru.harness.profile.EntryPatch
import im.hikaru.harness.profile.PatchValue
import im.hikaru.harness.profile.ProfileManifest
import im.hikaru.harness.profile.ProfilePatchReload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Strict codec for the portable subset of DSH cordis.patch.yml. */
public class ProfilePatchYamlCodec {
    private val yaml = Yaml(SafeConstructor(LoaderOptions()))

    public fun decode(
        content: String,
        source: String = "cordis.patch.yml",
    ): List<EntryPatch> {
        require(!JS_EXPRESSION_PATTERN.containsMatchIn(content)) {
            "$source uses !!js, which is not portable to the KMP runtime"
        }
        val root =
            try {
                yaml.load<Any?>(content)
            } catch (error: Throwable) {
                throw IllegalArgumentException("Invalid profile patch YAML: $source", error)
            }
        val rows = root as? List<*> ?: error("Profile patch root must be an array: $source")
        return rows.mapIndexed { index, raw ->
            decodePatch(raw.mapping("patch row $index", source), index, source)
        }
    }

    private fun decodePatch(
        row: Map<String, Any?>,
        index: Int,
        source: String,
    ): EntryPatch {
        row.requireOnly(PATCH_FIELDS, "patch row $index", source)
        return EntryPatch(
            id = row.optionalString("id", "patch row $index", source),
            insert =
                if ("insert" in row) {
                    val values = row["insert"] as? List<*>
                        ?: error("Patch row $index field insert must be an array: $source")
                    values.mapIndexed { insertedIndex, raw ->
                        decodeEntry(
                            raw.mapping("patch row $index insert $insertedIndex", source),
                            index,
                            insertedIndex,
                            source,
                        )
                    }
                } else {
                    null
                },
            name = row.optionalString("name", "patch row $index", source),
            config =
                if ("config" in row) {
                    PatchValue.Present(
                        anyToJson(row["config"], source)
                            .also(::rejectSecrets)
                            .takeUnless { it === JsonNull }
                    )
                } else {
                    PatchValue.Absent
                },
            disabled =
                if ("disabled" in row) {
                    PatchValue.Present(
                        row["disabled"] as? Boolean
                            ?: error("Patch row $index field disabled must be a boolean: $source")
                    )
                } else {
                    PatchValue.Absent
                },
        )
    }

    private fun decodeEntry(
        row: Map<String, Any?>,
        patchIndex: Int,
        insertIndex: Int,
        source: String,
    ): Entry {
        val location = "patch row $patchIndex insert $insertIndex"
        row.requireOnly(ENTRY_FIELDS, location, source)
        val id = row.requiredString("id", location, source)
        val name = row.requiredString("name", location, source)
        val config =
            if ("config" in row) {
                anyToJson(row["config"], source).also(::rejectSecrets).takeUnless { it === JsonNull }
            } else {
                null
            }
        val disabled =
            if ("disabled" !in row) {
                false
            } else {
                row["disabled"] as? Boolean
                    ?: error("$location field disabled must be a boolean: $source")
            }
        return Entry(id, name, config, disabled)
    }

    private companion object {
        val PATCH_FIELDS = setOf("id", "insert", "name", "config", "disabled")
        val ENTRY_FIELDS = setOf("id", "name", "config", "disabled")
        val JS_EXPRESSION_PATTERN = Regex("(?m)^[^#\\n]*!!js(?:\\s|$)")
    }
}

/** JSON package manifest codec for dsh.profile metadata. */
public object ProfileManifestCodec {
    private val json = Json { prettyPrint = true }

    public fun decode(content: String, source: String): ProfileManifest {
        val root = json.parseToJsonElement(content).jsonObject
        val name = root.requiredString("name", source)
        val profile = root["dsh"]?.jsonObject?.get("profile")?.jsonObject
            ?: error("Profile manifest is missing dsh.profile: $source")
        val bundles =
            profile["bundles"]?.jsonArray?.mapIndexed { index, value ->
                value.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
                    ?: error("Profile bundle at index $index must be a non-blank string: $source")
            } ?: error("Profile manifest is missing dsh.profile.bundles: $source")
        val reload =
            profile["patchReload"]?.jsonPrimitive?.contentOrNull
                ?: error("Profile manifest is missing dsh.profile.patchReload: $source")
        return ProfileManifest(
            name = name,
            bundles = bundles,
            patchReload = ProfilePatchReload.parse(reload),
        )
    }

    public fun encode(manifest: ProfileManifest): String =
        json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("name", JsonPrimitive(manifest.name))
                put("private", JsonPrimitive(true))
                put("dependencies", buildJsonObject {})
                put(
                    "dsh",
                    buildJsonObject {
                        put(
                            "profile",
                            buildJsonObject {
                                put(
                                    "bundles",
                                    buildJsonArray {
                                        manifest.bundles.forEach { add(JsonPrimitive(it)) }
                                    },
                                )
                                put("patchReload", JsonPrimitive(manifest.patchReload.manifestValue))
                            },
                        )
                    },
                )
            },
        ) + "\n"
}

private fun Any?.mapping(location: String, source: String): Map<String, Any?> {
    val raw = this as? Map<*, *> ?: error("$location must be an object: $source")
    return raw.map { (key, value) ->
        require(key is String) { "$location keys must be strings: $source" }
        key to value
    }.toMap(LinkedHashMap())
}

private fun Map<String, Any?>.requireOnly(
    fields: Set<String>,
    location: String,
    source: String,
) {
    val unsupported = keys - fields
    require(unsupported.isEmpty()) {
        "$location contains unsupported fields ${unsupported.sorted().joinToString()}: $source"
    }
}

private fun Map<String, Any?>.requiredString(
    key: String,
    location: String,
    source: String,
): String =
    optionalString(key, location, source)
        ?: error("$location field $key is required: $source")

private fun Map<String, Any?>.optionalString(
    key: String,
    location: String,
    source: String,
): String? {
    if (key !in this) return null
    return (get(key) as? String)?.takeIf(String::isNotBlank)
        ?: error("$location field $key must be a non-blank string: $source")
}

private fun JsonObject.requiredString(key: String, source: String): String =
    get(key)?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: error("Profile manifest field $key must be a non-blank string: $source")

private fun anyToJson(value: Any?, source: String): JsonElement =
    when (value) {
        null -> JsonNull
        is Map<*, *> ->
            buildJsonObject {
                value.forEach { (key, child) ->
                    require(key is String) { "Profile config keys must be strings: $source" }
                    put(key, anyToJson(child, source))
                }
            }
        is Iterable<*> -> buildJsonArray { value.forEach { add(anyToJson(it, source)) } }
        is Boolean -> JsonPrimitive(value)
        is Byte -> JsonPrimitive(value)
        is Short -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> error("Unsupported profile config value ${value::class.qualifiedName}: $source")
    }

private fun rejectSecrets(value: JsonElement) {
    when (value) {
        is JsonObject ->
            value.forEach { (key, child) ->
                require(key.lowercase().replace("_", "").replace("-", "") !in SECRET_FIELD_NAMES) {
                    "Profile patches must not contain secret field '$key'; use .credentials.yaml"
                }
                rejectSecrets(child)
            }
        is JsonArray -> value.forEach(::rejectSecrets)
        else -> Unit
    }
}

private val SECRET_FIELD_NAMES =
    setOf("apikey", "secret", "password", "token", "accesstoken", "refreshtoken")
