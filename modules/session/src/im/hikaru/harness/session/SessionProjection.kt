package im.hikaru.harness.session

import im.hikaru.harness.llm.Message

fun deriveMessages(events: List<SessionEventEnvelope>): List<Message> =
    events.mapNotNull { event ->
        if (event.surfaceOp != SessionSurfaceOperation.APPEND) {
            return@mapNotNull null
        }

        when (event.type) {
            SessionEventNames.USER_MESSAGE ->
                decode(UserMessageEvent.serializer(), event.data).message

            SessionEventNames.ASSISTANT_MESSAGE ->
                decode(AssistantMessageEvent.serializer(), event.data)
                    .message
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
