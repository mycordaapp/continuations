package mycorda.app.continuations.simple

import mycorda.app.continuations.*
import mycorda.app.rss.JsonSerialiser
import mycorda.app.types.ExceptionInfo

class SimpleContinuableWorkerClient(private val worker: ContinuableWorker) : ContinuableWorkerClient {
    private val rss = JsonSerialiser()

    override fun <T> schedule(scheduled: Schedule<T>) {
        val roundTripped = roundTripInput(scheduled)
        return worker.schedule(roundTripped)
    }

    override fun <O> result(id: ContinuationId): O {
        val roundTrippedId = roundTripId(id)
        return roundTripOutput(worker.result(roundTrippedId))
    }

    override fun exception(id: ContinuationId): ExceptionInfo {
        val roundTrippedId = roundTripId(id)
        return roundTripOutput(worker.exception(roundTrippedId))
    }

    override fun status(id: ContinuationId): ContinuationStatus {
        val roundTrippedId = roundTripId(id)
        return roundTripOutput(worker.status(roundTrippedId))
    }

    override fun info(id: ContinuationId): ContinuationInfo {
        val roundTrippedId = roundTripId(id)
        return roundTripOutput(worker.info(roundTrippedId))
    }

    override fun continuations(): ContinuationIdList {
        return roundTripOutput(worker.continuations())
    }


    private fun <I : Any> roundTripInput(input: I): I {
        @Suppress("UNCHECKED_CAST")
        return rss.deserialiseData(rss.serialiseData(input)).any() as I
    }

    private fun roundTripId(id: ContinuationId): ContinuationId {
        val roundTripped = roundTripInput(id.id())
        return ContinuationId.fromString(roundTripped)
    }

    private fun <O : Any> roundTripOutput(output: O): O {
        @Suppress("UNCHECKED_CAST")
        return rss.deserialiseData(rss.serialiseData(output)).any() as O
    }
}