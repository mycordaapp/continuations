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
    val time: Long = System.currentTimeMillis()
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