package mycorda.app.continuations


import mycorda.app.registry.Registry
import java.lang.Exception
import java.lang.Long.max
import kotlin.reflect.KClass

data class ContinuationContext(val attempts: Int = 0)

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
    fun get(continuationKey: String): Continuation
    fun exceptionStrategy(): ContinuationExceptionStrategy
}

/**
 * Create a SimpleContinuation
 */
class SimpleContinuationFactory(registry: Registry = Registry()) : ContinuationFactory {
    private val registry = registry.clone() // make a clean copy as registry is mutable
    private val lookup = HashMap<String, SimpleContinuation>()
    override fun get(continuationKey: String): Continuation {
        lookup.putIfAbsent(continuationKey, SimpleContinuation(exceptionStrategy(), registry))
        return lookup[continuationKey]!!
    }

    override fun exceptionStrategy(): ContinuationExceptionStrategy {
        return registry.geteOrElse(ContinuationExceptionStrategy::class.java, RetryNTimesExceptionStrategy())
    }
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

class SimpleScheduler(private val continuation: Continuation) : Scheduler {
    private val schedules = ArrayList<Scheduled<Any>>()
    override fun <T : Any> schedule(scheduled: Scheduled<T>) {
        schedules.add(scheduled as Scheduled<Any>)
    }

    override fun <T : Any> waitFor(key: String): T {
        val scheduled = schedules.single { it.key == key }
        val delayedRetry = max(scheduled.scheduledTime - System.currentTimeMillis(), 1)
        Thread.sleep(delayedRetry)
        schedules.remove(scheduled)
        return continuation.execBlock(key, scheduled.clazz, scheduled.block, scheduled.ctx) as T
    }
}



class SimpleContinuation(
    private val exceptionStrategy: ContinuationExceptionStrategy = RetryNTimesExceptionStrategy(),
    registry: Registry = Registry()
) : Continuation {
    private val scheduler = registry.geteOrElse(Scheduler::class.java, SimpleScheduler(this))
    private val lookup = HashMap<String, Any>()
    override fun <T : Any> execBlock(
        key: String,
        clazz: KClass<out T>,
        block: (ctx: ContinuationContext) -> T,
        ctx: ContinuationContext
    ): T {
        if (!lookup.containsKey(key)) {
            // step has not run successfully before

            try {
                val result = block.invoke(ctx)
                lookup[key] = result
                return result
            } catch (ex: Exception) {
                val retry = exceptionStrategy.handle(ctx, ex)
                when (retry) {
                    is ImmediateRetry -> {
                        if (retry.maxAttempts >= ctx.attempts) {
                            return this.execBlock(key, clazz, block, ctx)
                        }
                    }
                    is DelayedRetry -> {
                        if (retry.maxAttempts >= ctx.attempts) {
                            val scheduled = Scheduled(key, ctx, clazz, block)
                            scheduler.schedule(scheduled)
                            return scheduler.waitFor(key)
                        }
                    }
                    is DontRetry -> {
                        // todo - log before throwing
                        throw ex
                    }
                }
                // what to do here ?
                throw ex
            }
        } else {
            // step has run successfully and can be skipped
            return lookup[key] as T
        }
    }
}



class RestartableContinuation(
    registry: Registry = Registry(),
    private val exceptionStrategy: ContinuationExceptionStrategy = RetryNTimesExceptionStrategy()
) : Continuation {
    private val scheduler = registry.geteOrElse(Scheduler::class.java, SimpleScheduler(this))
    private val lookup = HashMap<String, Any>()
    override fun <T : Any> execBlock(
        key: String,
        clazz: KClass<out T>,
        block: (ctx: ContinuationContext) -> T,
        ctx: ContinuationContext
    ): T {
        if (!lookup.containsKey(key)) {
            // step has not run successfully before

            try {
                val result = block.invoke(ctx)
                lookup[key] = result
                return result
            } catch (ex: Exception) {
                val retry = exceptionStrategy.handle(ctx, ex)
                when (retry) {
                    is ImmediateRetry -> {
                        if (retry.maxAttempts >= ctx.attempts) {
                            return this.execBlock(key, clazz, block, ctx)
                        }
                    }
                    is DelayedRetry -> {
                        if (retry.maxAttempts >= ctx.attempts) {
                            val scheduled = Scheduled(key, ctx, clazz, block)
                            scheduler.schedule(scheduled)
                            return scheduler.waitFor(key)
                        }
                    }
                    is DontRetry -> {
                        // todo - log before throwing
                        throw ex
                    }
                }
                // what to do here ?
                throw ex
            }
        } else {
            // step has run successfully and can be skipped
            return lookup[key] as T
        }
    }
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
    fun handle(ctx: ContinuationContext, ex: Exception): RetryStrategy
    fun incrementRetries(ctx: ContinuationContext): ContinuationContext {
        return ctx.copy(attempts = ctx.attempts + 1)
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
        val scheduledTime = System.currentTimeMillis() + (ctx.attempts * initialDelayMs)
        return DelayedRetry(incrementRetries(ctx), scheduledTime, maxRetries)
    }
}



