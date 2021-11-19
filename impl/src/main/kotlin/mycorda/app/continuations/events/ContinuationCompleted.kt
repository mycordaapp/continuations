package mycorda.app.continuations.events

import mycorda.app.continuations.ContinuationId
import mycorda.app.ses.Event
import mycorda.app.ses.EventFactory
import mycorda.app.ses.EventQuery
import mycorda.app.ses.EventTypeQuery

object ContinuationCompletedFactory : EventFactory {

    fun create(continuationId: ContinuationId): Event {
        return Event(
            type = eventType(),
            aggregateId = continuationId.toString()
        )
    }

    fun eventType(): String = "mycorda.app.continuations.events.ContinuationCompleted"

    fun typeFilter() : EventQuery = EventTypeQuery(eventType())

}

