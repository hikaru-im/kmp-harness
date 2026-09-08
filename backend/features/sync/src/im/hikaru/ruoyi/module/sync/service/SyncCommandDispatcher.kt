package im.hikaru.ruoyi.module.sync.service

import org.springframework.stereotype.Component

@Component
class SyncCommandDispatcher(
    handlers: List<SyncCommandHandler>,
) {
    private val handlersByKey: Map<SyncCommandHandlerKey, SyncCommandHandler> = buildMap {
        handlers.forEach { handler ->
            handler.keys.forEach { key ->
                require(put(key, handler) == null) {
                    "Multiple sync handlers are registered for ${key.aggregateType}/${key.operation}"
                }
            }
        }
    }

    fun dispatch(context: SyncCommandContext, command: SyncCommandEnvelope): SyncCommandDecision {
        val key = SyncCommandHandlerKey(command.aggregateType, command.operation)
        val handler = handlersByKey[key]
            ?: return SyncCommandDecision.Rejected("UNSUPPORTED_COMMAND")
        return handler.handle(context, command)
    }
}
