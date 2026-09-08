package im.hikaru.harness.settings

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsServiceTest {
    @Test
    fun layersUserPatchAndRedactsSecrets() =
        runTest {
            val store = InMemorySettingsDocumentStore()
            val service = SettingsService(store)
            service.start()
            val scope =
                service.register(
                    namespace = settingsNamespace("llm-koog"),
                    defaults =
                        buildJsonObject {
                            put("enabled", false)
                            putJsonObject("provider") {
                                put("baseUrl", "https://default.example")
                            }
                        },
                    base =
                        buildJsonObject {
                            put("enabled", true)
                        },
                    secretPaths = setOf(listOf("credential")),
                )

            assertEquals(
                "https://default.example",
                scope.get()["provider"]!!.jsonObject["baseUrl"]!!.jsonPrimitive.content,
            )
            assertEquals(true, scope.get()["enabled"]!!.jsonPrimitive.content.toBoolean())

            scope.update(
                buildJsonObject {
                    put("credential", "OPENAI_API_KEY")
                    putJsonObject("provider") {
                        put("displayName", "OpenAI")
                    }
                },
            )

            assertEquals("OPENAI_API_KEY", scope.get()["credential"]!!.jsonPrimitive.content)
            assertEquals(
                "https://default.example",
                scope.get()["provider"]!!.jsonObject["baseUrl"]!!.jsonPrimitive.content,
            )
            assertEquals(
                "***",
                scope.describe().value["credential"]!!.jsonPrimitive.content,
            )
            assertEquals(
                1L,
                scope.describe().revision,
            )

            service.dispose()
        }

    @Test
    fun staleRevisionIsRejectedWithoutChangingTheDocument() =
        runTest {
            val service = SettingsService(InMemorySettingsDocumentStore())
            service.start()
            val scope = service.register(settingsNamespace("test"))
            scope.update(buildJsonObject { put("value", 1) })

            assertFailsWith<SettingsConflictException> {
                scope.update(
                    patch = buildJsonObject { put("value", 2) },
                    expectedRevision = 0,
                )
            }
            assertEquals(1, scope.get()["value"]!!.jsonPrimitive.content.toInt())
            service.dispose()
        }

    @Test
    fun validatorRejectsBeforePersistence() =
        runTest {
            val store = InMemorySettingsDocumentStore()
            val service = SettingsService(store)
            service.start()
            val scope =
                service.register(
                    namespace = settingsNamespace("validated"),
                    defaults = buildJsonObject { put("port", 8080) },
                    validator = SettingsValidator { value ->
                        require(value["port"]!!.jsonPrimitive.content.toInt() in 1..65535)
                    },
                )

            scope.update(buildJsonObject { put("port", 8080) })
            assertFailsWith<IllegalArgumentException> {
                scope.update(buildJsonObject { put("port", 0) })
            }
            assertEquals(8080, scope.get()["port"]!!.jsonPrimitive.content.toInt())
            assertEquals(
                8080,
                store.read()["validated"]!!.jsonObject["port"]!!.jsonPrimitive.content.toInt(),
            )
            service.dispose()
        }
}
