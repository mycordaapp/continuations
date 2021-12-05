package mycorda.app.continuations

import mycorda.app.types.ExceptionInfo


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

interface ScheduleContinuable {

    /**
     * Schedule a Continuable
     */
    fun <T> schedule(scheduled: Schedule<T>)


    /**
     * Retrieve the result of the continuation
     */
    fun <O> result(id: ContinuationId): O
    fun exception(id: ContinuationId): ExceptionInfo
    fun status(id: ContinuationId): ContinuationStatus
}
/**
 * The minimum services any Worker should expose.
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


    /**
     * Retrieve the result of the continuation
     */
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