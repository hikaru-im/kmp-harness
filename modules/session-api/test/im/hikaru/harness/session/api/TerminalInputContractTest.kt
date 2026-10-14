package im.hikaru.harness.session.api

import im.hikaru.harness.session.SessionId
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
class TerminalInputContractTest {
    private val owner = TerminalOwner(TerminalScope("scope"), SessionId("session"), TerminalIncarnation("incarnation"))
    private val key = TerminalKey(owner, TerminalSessionId("terminal"))

    @Test
    fun semanticInputsRoundTripWithoutPlatformObjects() {
        val inputs: List<TerminalInput> = listOf(
            TerminalInput.Text("中文🙂\n"), TerminalInput.Paste("a\r\nb\nc\r🙂"),
        ) + TerminalKeyName.entries.map { name ->
            TerminalInput.KeyPress(name, TerminalModifier.entries.toSet(), if (name == TerminalKeyName.Character) "A" else null)
        }
        val names = listOf("text", "paste") + List(TerminalKeyName.entries.size) { "key_press" }
        inputs.zip(names).forEach { (input, name) ->
            val encoded = Json.encodeToString<TerminalInput>(input)
            assertEquals(input, Json.decodeFromString<TerminalInput>(encoded))
            val obj = Json.parseToJsonElement(encoded).jsonObject
            assertEquals(name, obj.getValue("type").jsonPrimitive.content)
            assertEquals(if (name == "key_press") setOf("type", "key", "modifiers") +
                (if ((input as TerminalInput.KeyPress).character == null) emptySet() else setOf("character"))
                else setOf("type", "text"), obj.keys)
            for (forbidden in listOf("AWT", "JediTerm", "keyCode", "Process", "byte[]")) assertFalse(encoded.contains(forbidden))
        }
        val descriptor = TerminalInput.KeyPress.serializer().descriptor
        assertEquals(listOf("key", "modifiers", "character"),
            (0 until descriptor.elementsCount).map { descriptor.getElementName(it) })
        assertTrue(descriptor.isElementOptional(1))
        assertTrue(descriptor.isElementOptional(2))
        assertEquals(TerminalInput.KeyPress(TerminalKeyName.Enter),
            Json.decodeFromString<TerminalInput>("{\"type\":\"key_press\",\"key\":\"Enter\"}"))
        val mutable = mutableSetOf(TerminalModifier.Control)
        val captured = TerminalInput.KeyPress(TerminalKeyName.Character, mutable, "c")
        mutable.clear()
        mutable.add(TerminalModifier.Meta)
        assertEquals(setOf(TerminalModifier.Control), captured.modifiers)
        assertEquals(31, TerminalKeyName.entries.size)
        assertEquals(setOf("Shift", "Control", "Alt", "Meta"), TerminalModifier.entries.map { it.name }.toSet())
        assertFalse(TerminalInput.serializer().descriptor.toString().contains("preedit", ignoreCase = true))
    }

    @Test
    fun keyPressCharacterInvariantsAreEnforced() {
        for (code in 0x20..0x7e) {
            assertEquals(code.toChar().toString(), TerminalInput.KeyPress(TerminalKeyName.Character, character = code.toChar().toString()).character)
        }
        for (character in listOf(null, "", "ab", "\u001f", "\u007f", "中", "🙂", "\uD800", "\uDC00")) {
            assertFailsWith<IllegalArgumentException> { TerminalInput.KeyPress(TerminalKeyName.Character, character = character) }
        }
        for (name in TerminalKeyName.entries.filter { it != TerminalKeyName.Character }) {
            assertNull(TerminalInput.KeyPress(name).character)
            for (character in listOf("", "a", "\u001b")) {
                assertFailsWith<IllegalArgumentException> { TerminalInput.KeyPress(name, character = character) }
            }
        }
        for (encoded in listOf(
            "{\"type\":\"key_press\",\"key\":\"Character\"}",
            "{\"type\":\"key_press\",\"key\":\"Character\",\"character\":\"🙂\"}",
            "{\"type\":\"key_press\",\"key\":\"Enter\",\"character\":\"x\"}",
        )) assertFails { Json.decodeFromString<TerminalInput>(encoded) }
    }

    @Test
    fun legacyServiceSemanticInputIsTypedUnavailable() = runBlocking<Unit> {
        val legacy = object : SessionTerminalsService {
            override suspend fun create(request: TerminalCreateRequest): TerminalCreateResult = TerminalCreateResult.Unavailable(request.owner, "legacy")
            override suspend fun list(owner: TerminalOwner): TerminalListResult = TerminalListResult.Unavailable(owner, "legacy")
            override suspend fun read(key: TerminalKey): TerminalReadResult = TerminalReadResult.Unavailable(key, "legacy")
            override suspend fun write(key: TerminalKey, input: String): TerminalMutationResult = TerminalMutationResult.Unavailable(key, "legacy")
            override suspend fun resize(key: TerminalKey, size: TerminalSize): TerminalMutationResult = TerminalMutationResult.Unavailable(key, "legacy")
            override suspend fun close(key: TerminalKey): TerminalMutationResult = TerminalMutationResult.Unavailable(key, "legacy")
            override suspend fun shutdown(owner: TerminalOwner): TerminalShutdownResult = TerminalShutdownResult.Rejected(owner, "legacy")
        }
        val requested = key.copy(owner = owner.copy(scope = TerminalScope("requested-scope"),
            session = SessionId("requested-session"), incarnation = TerminalIncarnation("requested-incarnation")),
            terminal = TerminalSessionId("requested-terminal"))
        for (input in listOf(TerminalInput.Text("中文🙂"), TerminalInput.Paste("paste"), TerminalInput.KeyPress(TerminalKeyName.Enter))) {
            assertEquals(requested, assertIs<TerminalMutationResult.Unavailable>(legacy.input(requested, input)).key)
        }
    }

    @Test
    fun semanticResultRetainsRequestedIdentityAndByteCount() {
        val requested = key.copy(owner = owner.copy(incarnation = TerminalIncarnation("replacement")))
        val bytes = "中文🙂".encodeToByteArray().size
        assertEquals(10, bytes)
        val results: List<TerminalMutationResult> = listOf(
            TerminalMutationResult.Applied(requested, bytes), TerminalMutationResult.Rejected(requested, "known"),
            TerminalMutationResult.Unavailable(requested, "legacy"), TerminalMutationResult.NotFound(requested),
            TerminalMutationResult.UnknownOutcome(requested, "delivery"),
        )
        for (result in results) {
            val decoded = Json.decodeFromString<TerminalMutationResult>(Json.encodeToString(result))
            assertEquals(result, decoded)
            assertEquals(requested, decoded.key)
        }
        assertEquals(bytes, assertIs<TerminalMutationResult.Applied>(results.first()).bytes)
        assertFailsWith<IllegalArgumentException> { TerminalMutationResult.Applied(requested, -1) }
    }
}
