package mycorda.app.continuations.events

import mycorda.app.continuations.ContinuationId
import mycorda.app.ses.Event
import mycorda.app.ses.EventFactory

data class ContinuationFailed(val exception: String, val message: String)

object ContinuationFailedFactory : EventFactory {

    fun create(continuationId: ContinuationId, t: Throwable): Event {
        return Event(
            type = eventType(),
            aggregateId = continuationId.toString(),
            payload = ContinuationFailed(
                exception = t::class.qualifiedName ?: "unknown",
                message = t.message ?: "unknown"
            )
        )
    }

    override fun eventType(): String = "mycorda.app.continuations.events.ContinuationFailed"

}

