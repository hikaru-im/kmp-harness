package im.hikaru.harness.session

object SessionEventNames {
    const val TURN_START = "turn/start"
    const val TURN_END = "turn/end"
    const val STEP_START = "step/start"
    const val STEP_END = "step/end"
    const val USER_MESSAGE = "user/message"
    const val ASSISTANT_CHUNK = "assistant/chunk"
    const val ASSISTANT_MESSAGE = "assistant/message"
    const val REQUEST_HEADER = "request/header"
    const val REQUEST_CONTEXT = "request/context"
}

object SessionEventKeys {
    val TurnStart =
        SessionEventKey(SessionEventNames.TURN_START, TurnStartEvent.serializer())
    val TurnEnd =
        SessionEventKey(SessionEventNames.TURN_END, TurnEndEvent.serializer())
    val StepStart =
        SessionEventKey(SessionEventNames.STEP_START, StepStartEvent.serializer())
    val StepEnd =
        SessionEventKey(SessionEventNames.STEP_END, StepEndEvent.serializer())
    val UserMessage =
        SurfaceSessionEventKey(SessionEventNames.USER_MESSAGE, UserMessageEvent.serializer())
    val AssistantChunk =
        SessionEventKey(SessionEventNames.ASSISTANT_CHUNK, AssistantChunkEvent.serializer())
    val AssistantMessage =
        SurfaceSessionEventKey(SessionEventNames.ASSISTANT_MESSAGE, AssistantMessageEvent.serializer())
    val RequestHeader =
        SessionEventKey(SessionEventNames.REQUEST_HEADER, RequestHeaderEvent.serializer())
    val RequestContext =
        SessionEventKey(SessionEventNames.REQUEST_CONTEXT, RequestContextEvent.serializer())
}
