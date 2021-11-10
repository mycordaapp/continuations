package mycorda.app.continuations

import mycorda.app.chaos.Chaos
import mycorda.app.chaos.FailNPercent
import mycorda.app.chaos.Noop
import mycorda.app.helpers.random
import mycorda.app.registry.Registry
import mycorda.app.xunitpatterns.spy.Spy
import java.lang.Exception
import java.lang.Long.max
import kotlin.reflect.KClass

data class ContinuationContext(val attempts: Int = 0)

/**
 * The basic definition of a Continuation. A block of code that
 * is associated with a unique (within the Continuation)
 *
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


class ThreeStepClass(
    registry: Registry = Registry(),
    continuationKey: String = String.random()
) {
    // setup continuation
    private val factory = registry.geteOrElse(ContinuationFactory::class.java, SimpleContinuationFactory(registry))
    private val continuation = factory.get(continuationKey)

    // setup test support
    private val chaos = registry.geteOrElse(Chaos::class.java, Chaos(emptyMap(), true))
    private val spy = registry.geteOrElse(Spy::class.java, Spy())

    fun exec(startNumber: Int): Int {
        // run a sequence of calculations
        val step1Result = continuation.execBlock("step1", 1::class) {
            testDecoration("step1")
            startNumber * startNumber
        }
        val step2Result = continuation.execBlock("step2", 1::class) {
            testDecoration("step2")
            step1Result + 1
        }
        return continuation.execBlock("step3", 1::class) {
            testDecoration("step3")
            step2Result + step2Result
        }
    }

    // only to control and observer the test double - wouldn't expect this in real code
    private fun testDecoration(step: String) {
        spy.spy(step)
        chaos.chaos(step)
    }
}

fun main() {

    // no chaos, inbuilt continuation
    simplest()

    // no chaos, continuation provided by registry
    continuationInRegistry()

    // step 2 fails, then we retry the continuation to completion
    failStep2ThenRetryContinuation()

}

private fun failStep2ThenRetryContinuation() {
    // 1 - setup
    val key = String.random()
    val chaos = Chaos(
        mapOf(
            "step1" to listOf(Noop()),
            "step2" to listOf(FailNPercent(100)),
            "step3" to listOf(Noop()),
        ),
        true
    )
    val spy = Spy()
    val continuationFactory = SimpleContinuationFactory()

    // 2 - run continuation - should fail on step 2
    try {
        ThreeStepClass(
            registry = Registry().store(continuationFactory).store(chaos).store(spy),
            continuationKey = key
        ).exec(10)
    } catch (ex: Exception) {
    }

    // 3 - run continuation again - should skip step 1 and complete
    val result = ThreeStepClass(
        registry = Registry().store(continuationFactory).store(spy),
        continuationKey = key
    ).exec(10)
    println(result)

    // 4 - spy to see that steps are executed as expected - step1 should be skipped
    println(spy.secrets())
}

private fun continuationInRegistry() {
    // no chaos, continuation provided by registry
    val result = ThreeStepClass(Registry().store(SimpleContinuation())).exec(10)
    println(result)
}

private fun simplest() {
    // no chaos, inbuilt continuation
    val result = ThreeStepClass().exec(10)
    println(result)
}