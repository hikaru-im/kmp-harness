package im.hikaru.harness.llm.koog.openai.client

import im.hikaru.harness.llm.koog.KoogCredentialRef
import im.hikaru.harness.llm.koog.KoogCredentialResolver

/** Resolves static-profile credential references from environment variables. */
public class EnvironmentKoogCredentialResolver(
    private val environment: (String) -> String? = System::getenv,
) : KoogCredentialResolver {
    override suspend fun resolve(reference: KoogCredentialRef): String? =
        environment(reference.name.toEnvironmentVariable())

    private fun String.toEnvironmentVariable(): String =
        uppercase().map { character ->
            if (character in 'A'..'Z' || character in '0'..'9' || character == '_') {
                character
            } else {
                '_'
            }
        }.joinToString("")
}
