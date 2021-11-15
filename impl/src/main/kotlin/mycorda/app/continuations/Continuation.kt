package mycorda.app.continuations

import mycorda.app.types.StringList
import java.lang.Exception
import java.util.*
import kotlin.reflect.KClass


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
    val errorStack: StringList = StringList(emptyList())
)

/**
 * A type safe Id associated with a continuation. Current it is
 * internally just a UUID, but this could change
 */
class ContinuationId private constructor(private val id: String) {
    fun id(): String = id

    companion object {
        fun random(): ContinuationId {
            return ContinuationId(UUID.randomUUID().toString())
        }

        fun fromString(id: String): ContinuationId {
            UUID.fromString(id) // checks it a UUID Id
            return ContinuationId(id)
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (other is ContinuationId) {
            this.id() == other.id()
        } else false
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}


interface Restartable<I, O> {
    fun exec(input: I): O
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

interface SchedulerFactory {
    fun get(continuation: Continuation): Scheduler
}


/**
 * Something that can be scheduled to run at some point in the future
 */
data class Scheduled<out T : Any>(
    val key: String,
    val ctx: ContinuationContext,
    val clazz: KClass<out T>, // can I get rid of this
    val block: (ctx: ContinuationContext) -> T,
    val scheduledTime: Long = System.currentTimeMillis() + 1000
)

interface Scheduler {
    fun <T : Any> schedule(scheduled: Scheduled<T>)
    fun <T : Any> waitFor(key: String): T
}

//
//class RestartableContinuation(
//    registry: Registry = Registry(),
//    private val exceptionStrategy: ContinuationExceptionStrategy = RetryNTimesExceptionStrategy()
//) : Continuation {
//    private val scheduler = registry.getOrElse(Scheduler::class.java, SimpleScheduler(this))
//    private val lookup = HashMap<String, Any>()
//    override fun <T : Any> execBlock(
//        key: String,
//        clazz: KClass<out T>,
//        block: (ctx: ContinuationContext) -> T,
//        ctx: ContinuationContext
//    ): T {
//        if (!lookup.containsKey(key)) {
//            // step has not run successfully before
//
//            try {
//                val result = block.invoke(ctx)
//                lookup[key] = result
//                return result
//            } catch (ex: Exception) {
//                val retry = exceptionStrategy.handle(ctx, ex)
//                when (retry) {
//                    is ImmediateRetry -> {
//                        if (retry.maxAttempts >= ctx.attempts) {
//                            return this.execBlock(key, clazz, block, ctx)
//                        }
//                    }
//                    is DelayedRetry -> {
//                        if (retry.maxAttempts >= ctx.attempts) {
//                            val scheduled = Scheduled(key, ctx, clazz, block)
//                            scheduler.schedule(scheduled)
//                            return scheduler.waitFor(key)
//                        }
//                    }
//                    is DontRetry -> {
//                        // todo - log before throwing
//                        throw ex
//                    }
//                }
//                // what to do here ?
//                throw ex
//            }
//        } else {
//            // step has run successfully and can be skipped
//            return lookup[key] as T
//        }
//    }
//}


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
        val newStack = StringList(ctx.errorStack)
        newStack.add(0, "${ex::class.qualifiedName}: ${ex.message!!}")
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



