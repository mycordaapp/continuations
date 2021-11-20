package mycorda.app.continuations.events

import mycorda.app.continuations.ContinuationId
import mycorda.app.ses.Event
import mycorda.app.ses.EventFactory

object ContinuationStartedFactory : EventFactory {

    fun create(continuationId: ContinuationId): Event {

        return Event(
            type = eventType(),
            aggregateId = continuationId.toString()
        )
    }

    override fun eventType(): String = "mycorda.app.continuations.events.ContinuationStarted"
}

