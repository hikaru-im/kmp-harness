package im.hikaru.contracts.harness.protocol

import kotlinx.serialization.Serializable

/** Host 或 Client 支持的 Harness 能力及其独立演进版本。 */
@Serializable
public data class Capability(
    val id: CapabilityId,
    val version: Int = 1,
) {
    init {
        require(version >= 1) {
            "Capability version must be at least 1"
        }
    }
}

internal fun validateCapabilities(
    owner: String,
    capabilities: List<Capability>,
) {
    val duplicateIds =
        capabilities
            .groupingBy(Capability::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

    require(duplicateIds.isEmpty()) {
        "$owner capabilities must not contain duplicate ids: " +
            duplicateIds.joinToString()
    }
}

internal fun List<Capability>.supportsCapability(
    id: CapabilityId,
    minimumVersion: Int,
): Boolean {
    require(minimumVersion >= 1) {
        "Minimum capability version must be at least 1"
    }

    return any { capability ->
        capability.id == id && capability.version >= minimumVersion
    }
}
