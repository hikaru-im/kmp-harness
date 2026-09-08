package im.hikaru.harness.credentials

private val CREDENTIAL_REF_PATTERN =
    Regex("^[A-Za-z_][A-Za-z0-9_]*$")

@JvmInline
value class CredentialRef(
    val name: String,
) {
    init {
        require(CREDENTIAL_REF_PATTERN.matches(name)) {
            "Credential reference must match ${CREDENTIAL_REF_PATTERN.pattern}"
        }
    }

    override fun toString(): String =
        name
}

fun credentialRef(value: String): CredentialRef =
    CredentialRef(value)

data class ResolvedCredential(
    val value: String,
    val source: String,
)

data class CredentialInfo(
    val configured: Boolean,
    val source: String? = null,
    val writable: Boolean,
)
