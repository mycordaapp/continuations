package mycorda.app.continuations


import mycorda.app.types.UniqueId
import java.lang.Exception
import java.util.*
import kotlin.reflect.KClass

// are these good candidates for commons ?
data class ExceptionInfo(val clazz: KClass<out Throwable>, val message: String) {
    constructor(ex: Throwable) : this(ex::class, ex.message!!)
}

class ExceptionInfoList(data: List<ExceptionInfo>) : ArrayList<ExceptionInfo>(data) {
    constructor() : this(emptyList())
}

data class ContinuationContext(
    /**
     * The number of previous attempts.
     */
    val attempts: Int = 0,
    /**
     * A stack like list of the error messages (i.e. latest is at the top).
     * Handlers can use this to help fine tune the retry logic. By default its set to
     * the exception message.
     */
    val errorStack: ExceptionInfoList = ExceptionInfoList()
)

/**
 * A type safe Id associated with a continuation. Currently it is
 * internally just a UUID, but this could change
 */
class ContinuationId private constructor(private val id: String) {
    fun id(): String = id

    fun toUniqueId(): UniqueId = UniqueId(id())

    companion object {
        fun random(): ContinuationId = ContinuationId(UUID.randomUUID().toString())

        fun fromString(id: String): ContinuationId {
            UUID.fromString(id) // checks it a UUID Id
            return ContinuationId(id)
        }

        fun fromUniqueId(uniqueId: UniqueId): ContinuationId = ContinuationId(uniqueId.toString())
    }

    override fun equals(other: Any?): Boolean {
        return if (other is ContinuationId) {
            this.id() == other.id()
        } else false
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String = id
}


/**
 * The basic definition of a Continuation. A block of code that
 * is associated with a unique (within the Continuation)
 */
interface Continuation {
    fun <T : Any> execBlock(
        key: String,
        clazz: KClass<out T>, // can I get rid of this?
        block: (ctx: ContinuationContext) -> T,
        ctx: ContinuationContext
    ): T

    fun <T : Any> execBlock(
        key: String,
        clazz: KClass<out T>, // can I get rid of this?
        block: (ctx: ContinuationContext) -> T
    ): T {
        return execBlock(key, clazz, block, ContinuationContext())
    }
}

/**
 * Abstract building and managing a continuation
 */
interface ContinuationFactory {
    fun get(continuationId: ContinuationId): Continuation
    fun exceptionStrategy(): ContinuationExceptionStrategy
}


/**
 * A RetryStrategy holds the newContext (that will be invoked on the
 * retry and the type of the retry
 */
sealed class RetryStrategy(private val newContext: ContinuationContext) {
    fun newContext(): ContinuationContext = newContext
}

data class ImmediateRetry(
    private val newContext: ContinuationContext,
    val maxAttempts: Int = 10
) : RetryStrategy(newContext)

data class DelayedRetry(
    private val newContext: ContinuationContext,
    val scheduledTime: Long,
    val maxAttempts: Int = 10
) : RetryStrategy(newContext)

data class DontRetry(private val newContext: ContinuationContext) : RetryStrategy(newContext)


/**
 * A way of plugin in a RetryStrategy based on the type of exception
 * and the ContinuationContext.
 */
interface ContinuationExceptionStrategy {
    /**
     * Handle the exception and apply the appropriate RetryStrategy
     */
    fun handle(ctx: ContinuationContext, ex: Exception): RetryStrategy

    /**
     * A helper to run the standard logic for keeping track of the
     */
    fun incrementRetriesHelper(ctx: ContinuationContext, ex: Exception): ContinuationContext {
        val newStack = ExceptionInfoList(ctx.errorStack)
        newStack.add(0, ExceptionInfo(ex))
        return ctx.copy(attempts = ctx.attempts + 1, errorStack = newStack)
    }
}

class FailImmediatelyExceptionStrategy : ContinuationExceptionStrategy {
    override fun handle(ctx: ContinuationContext, ex: Exception): RetryStrategy = DontRetry(ctx)
}


/**
 * Simply keep retrying for ever and double the delay
 * on each attempt.
 */
class RetryNTimesExceptionStrategy(
    private val maxRetries: Int = 10,
    private val initialDelayMs: Long = 10
) : ContinuationExceptionStrategy {
    override fun handle(
        ctx: ContinuationContext,
        ex: Exception
    ): RetryStrategy {
        val scheduledTime = System.currentTimeMillis() + ((ctx.attempts + 1) * initialDelayMs)
        return DelayedRetry(incrementRetriesHelper(ctx, ex), scheduledTime, maxRetries)
    }
}



