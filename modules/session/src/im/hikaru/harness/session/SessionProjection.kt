package im.hikaru.harness.session

import im.hikaru.harness.llm.Message

/** One display-title rule shared by live and durable conversation catalogs. */
fun defaultConversationTitle(messages: List<Message>): String? = messages
    .firstOrNull { it.source == im.hikaru.harness.llm.UserMessageSource }?.content
    ?.filterIsInstance<im.hikaru.harness.llm.TextBlock>()?.joinToString(" ") { it.text }
    ?.replace(Regex("\\s+"), " ")?.trim()?.take(80)?.takeIf { it.isNotEmpty() }

fun deriveMessages(events: List<SessionEventEnvelope>): List<Message> =
    events.mapNotNull { event ->
        if (event.surfaceOp != SessionSurfaceOperation.APPEND) {
            return@mapNotNull null
        }

        when (event.type) {
            SessionEventNames.USER_MESSAGE ->
                decode(UserMessageEvent.serializer(), event.data).message.copy(timestampMillis = event.time)

            SessionEventNames.ASSISTANT_MESSAGE ->
                decode(AssistantMessageEvent.serializer(), event.data)
                    .message
                    .copy(timestampMillis = event.time)
                    .takeIf { message -> message.content.isNotEmpty() }

            else -> null
        }
    }

fun foldRequestHeader(events: List<SessionEventEnvelope>): EpochHeader? =
    events.asReversed()
        .firstOrNull { event -> event.type == SessionEventNames.REQUEST_HEADER }
        ?.let { event ->
            canonicalHeader(
                decode(RequestHeaderEvent.serializer(), event.data).header
            )
        }

fun foldRequestContext(events: List<SessionEventEnvelope>): SessionRequestContext? =
    events.asReversed()
        .firstOrNull { event -> event.type == SessionEventNames.REQUEST_CONTEXT }
        ?.let { event ->
            decode(RequestContextEvent.serializer(), event.data).context.copy()
        }
