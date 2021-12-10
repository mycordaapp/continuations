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

/**
 * The possible states for a Continuation
 */
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
}


class ContinuationIdList(data: List<ContinuationId>) : ArrayList<ContinuationId>(data)

interface QueryContinuables {
    fun continuations(): ContinuationIdList
}

interface QueryContinuable {
    /**
     * Retrieve the result of the continuation. Only valid is the status is `Completed`
     */
    fun <O> result(id: ContinuationId): O

    /**
     * Retrieve the exception. Only valid is status is `Failed`
     */
    fun exception(id: ContinuationId): ExceptionInfo

    /**
     * Retrieve the status
     */
    fun status(id: ContinuationId): ContinuationStatus

    /**
     * Retrieve the status
     */
    fun info(id: ContinuationId): ContinuationInfo
}

interface ServiceLifeCycle {
    fun startup()
    fun shutdown()
}

/**
 * The minimum services any Worker should expose.
 */
interface ContinuableWorker : ScheduleContinuable, QueryContinuable, QueryContinuables, ServiceLifeCycle

/**
 * The client side of a Worker should expose.
 */
interface ContinuableWorkerClient : ScheduleContinuable, QueryContinuable, QueryContinuables

/**
 * Optional interface that gives access to the state of the Threads.
 * TODO: Is this is good idea?? - its really only for testing
 */
interface ContinuableWorkerThreadMonitor {
    fun threadId(id: ContinuationId): Long
}

