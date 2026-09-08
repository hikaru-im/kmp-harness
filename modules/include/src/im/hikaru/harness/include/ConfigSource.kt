package im.hikaru.harness.include

import im.hikaru.harness.loader.Entry

/** A decoded configuration snapshot with the resource's revision token. */
public data class ConfigSnapshot(
    val entries: List<Entry>,
    val revision: String? = null,
)

public fun interface ConfigSource {
    public suspend fun load(): ConfigSnapshot
}

public interface MutableConfigSource : ConfigSource {
    public suspend fun save(
        entries: List<Entry>,
        expectedRevision: String? = null,
    ): ConfigSnapshot
}

/** Composes resource I/O with a format codec. */
public open class DecodingConfigSource(
    private val resource: ConfigResource,
    private val codec: EntryCodec,
) : ConfigSource {
    override suspend fun load(): ConfigSnapshot =
        resource.read().decode(codec)
}

public class MutableDecodingConfigSource(
    private val resource: MutableConfigResource,
    private val codec: EntryCodec,
) : MutableConfigSource {
    override suspend fun load(): ConfigSnapshot =
        resource.read().decode(codec)

    override suspend fun save(
        entries: List<Entry>,
        expectedRevision: String?,
    ): ConfigSnapshot =
        resource.write(
            content = codec.encode(entries),
            expectedRevision = expectedRevision,
        ).decode(codec)
}

private fun ResourceSnapshot.decode(codec: EntryCodec): ConfigSnapshot =
    ConfigSnapshot(
        entries = codec.decode(content),
        revision = revision,
    )
