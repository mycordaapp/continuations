package mycorda.app.continuations.events

import mycorda.app.continuations.ContinuationContext
import mycorda.app.continuations.Scheduled
import mycorda.app.ses.Event
import mycorda.app.ses.EventFactory

data class ScheduledActionCreated(
    val key: String,
    val ctx: ContinuationContext,
    val clazzName: String,
    val scheduledTime: Long
)

object ScheduledActionCreatedFactory : EventFactory {

    fun create(action: Scheduled<Any>): Event {

        val payload = ScheduledActionCreated(
            key = action.key,
            ctx = action.ctx,
            clazzName = action.clazz.qualifiedName!!,
            scheduledTime = action.scheduledTime
        )
        return Event(
            type = "mycorda.app.continuations.events.ScheduledActionCreated",
            aggregateId = action.key,
            payload = payload
        )
    }

}