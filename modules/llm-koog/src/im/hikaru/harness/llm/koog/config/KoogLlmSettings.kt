package im.hikaru.harness.llm.koog

import kotlinx.serialization.json.JsonObject

/** Persisted, Provider-neutral settings owned by the Koog LLM plugin. */
data class KoogLlmSettings(
    val providers: Map<String, KoogProviderSettings>,
) {
    init {
        require(providers.keys.all(String::isNotBlank)) {
            "Koog LLM settings provider ids must not be blank"
        }
        require(providers.all { (id, settings) -> id == settings.provider }) {
            "Koog LLM settings map keys must match provider ids"
        }
    }

    fun toJson(): JsonObject = KoogLlmSettingsJsonCodec.encode(this)

    companion object {
        fun fromRoutes(routes: List<KoogProviderRoute>): KoogLlmSettings =
            KoogLlmSettings(
                routes.associate { route ->
                    route.id to
                        KoogProviderSettings(
                            provider = route.id,
                            displayName = route.name,
                        )
                }
            )

        fun fromJson(value: JsonObject): KoogLlmSettings =
            KoogLlmSettingsJsonCodec.decode(value)
    }
}
