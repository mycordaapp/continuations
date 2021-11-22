package mycorda.app.continuations.events

import mycorda.app.continuations.ContinuationId
import mycorda.app.ses.Event
import mycorda.app.ses.EventFactory

data class ContinuationStarted(
    val continuationIdAsString: String,
    val threadId: Long
) {
    constructor(continuationId: ContinuationId, threadId: Long) : this(continuationId.toString(), threadId)
}

object ContinuationStartedFactory : EventFactory {

    fun create(payload: ContinuationStarted): Event {

        return Event(
            type = eventType(),
            aggregateId = payload.continuationIdAsString,
            payload = payload
        )
    }

    override fun eventType(): String = "mycorda.app.continuations.events.ContinuationStarted"
}

