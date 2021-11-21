package mycorda.app.continuations

/**
 * The core concept. A `Continuation` is `Continuable` if
 * it can be recalled
 *
 * A `Continuable` must also implement a constructor with the signature:
 *     registry: Registry,
 *     continuationId: ContinuationId
 *
 */
interface Continuable<I, O> {
    fun exec(input: I): O
}

data class Schedule<T>(
    val continuableName: String,
    val id: ContinuationId,
    val input: T,
    val time: Long
)

enum class ContinuationStatus {
    UnknownContinuation,
    NotStarted,
    Running,
    Completed,
    Failed
}

interface ContinuableWorker {
    fun <T> schedule(scheduled: Schedule<T>)
    fun <O> result(id: ContinuationId): O
    fun status(id: ContinuationId): ContinuationStatus
}