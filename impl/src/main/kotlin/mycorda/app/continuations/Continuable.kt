package mycorda.app.continuations

import mycorda.app.types.ExceptionInfo

/**
 * The core concept. A `Continuable` is simply a
 * standard pattern for scheduling and restarting `Continuations`. Its not mandatory
 * to use the the Continuable pattern with a Continuable, but without it a similar
 * concept would be probably be required in order to build a robust application.
 *
 * An implementation of `Continuable` must have a constructor that takes
 * a Registry and a ContinuationId as parameters, e.g.
 *
 * The single entry point is the `exec` method.
 *
 * <pre>
 *  class MyContinuable (private val registry: Registry,
 *                       private val continuationId: ContinuationId) : Continuable<String,String> {
 *
 *  // implementation
 * }
 * </pre>
 */
interface Continuable<I, O> {
    fun exec(input: I): O
}

/**
 * Schedule a new `Continuable` to run.
 */
data class Schedule<T>(
    val continuableName: String,
    val id: ContinuationId,
    val input: T,
    val startTime: Long = System.currentTimeMillis()
)

enum class ContinuationStatus {
    UnknownContinuation,
    NotStarted,
    Running,
    Completed,
    Failed
}


data class ContinuationInfo(
    val id: ContinuationId,
    val status: ContinuationStatus,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val platformId: String? = null,
    val totalTime: Long? = if (startedAt != null && finishedAt != null) {
        finishedAt - startedAt
    } else null
)

/**
 * The minimum services any Worker should expose
 */
interface ContinuableWorker {
    /**
     * A hook for any startup processing, such as restarting running
     * continuations
     */
    fun startup()

    /**
     * Schedule a Continuable
     */
    fun <T> schedule(scheduled: Schedule<T>)
    fun <O> result(id: ContinuationId): O
    fun exception(id: ContinuationId): ExceptionInfo
    fun status(id: ContinuationId): ContinuationStatus
    fun continuations(): Iterable<ContinuationId>
}

/**
 * Optional interface that gives access to the state of the Threads
 */
interface ContinuableWorkerThreadMonitor {
    fun threadId(id: ContinuationId): Long
}