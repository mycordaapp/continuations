package mycorda.app.tasks.orchestration

import mycorda.app.chaos.Chaos
import mycorda.app.chaos.FailNPercent
import mycorda.app.chaos.Noop
import mycorda.app.helpers.random
import mycorda.app.registry.Registry
import mycorda.app.xunitpatterns.spy.Spy
import java.lang.Exception
import kotlin.reflect.KClass

data class ContinuationContext(val attempts: Int = 0)


interface Continuation {
    fun <T : Any> execBlock(
        key: String,
        clazz: KClass<out T>, // can I get rid of this?
        block: (ctx: ContinuationContext) -> T
    ): T
}

interface ContinuationFactory {
    fun get(continuationKey: String): Continuation
}

class SimpleContinuationFactory(registry: Registry = Registry()) : ContinuationFactory {
    private val registry = registry.clone() // make a clean copy as registry is mutable
    private val lookup = HashMap<String, SimpleContinuation>()
    override fun get(continuationKey: String): Continuation {
        lookup.putIfAbsent(continuationKey, SimpleContinuation(registry))
        return lookup[continuationKey]!!
    }
}

data class Scheduled<T : Any>(
    val key: String,
    val ctx: ContinuationContext,
    val clazz: KClass<out T>, // can I get rid of this
    val block: (ctx: ContinuationContext) -> T
)

interface Scheduler {
    fun <T : Any> schedule(scheduled: Scheduled<T>)
}

class SimpleScheduler : Scheduler {
    private val schedules = ArrayList<Scheduled<Any>>()
    override fun <T : Any> schedule(scheduled: Scheduled<T>) {
        schedules.add(scheduled as Scheduled<Any>)
    }

}

class SimpleContinuation(registry: Registry = Registry()) : Continuation {
    private val exceptionStrategy =
        registry.getOrNull(ContinuationExceptionStrategy::class.java)
    private val lookup = HashMap<String, Any>()
    override fun <T : Any> execBlock(key: String, clazz: KClass<out T>, block: (ctx: ContinuationContext) -> T): T {
        if (!lookup.containsKey(key)) {
            // step has not run successfully
            val ctx = ContinuationContext()

//            if (exceptionStrategy != null) {
//                try {
//                    block.invoke(ctx)
//                } catch (ex: Exception) {
//                    val retry = exceptionStrategy.handle(ctx, ex)
//                }
//            }
            val result = block.invoke(ctx)
            lookup[key] = result
            return result
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

data class ImmediateRetry(private val newContext: ContinuationContext) : RetryStrategy(newContext)
data class DontRetry(private val newContext: ContinuationContext) : RetryStrategy(newContext)
data class DelayedRetry(private val newContext: ContinuationContext, val scheduledTime: Long) :
    RetryStrategy(newContext)

/**
 * A way of plugin in a RetryStrategy based on the type of exception
 * and the ContinuationContext.
 */
interface ContinuationExceptionStrategy {
    fun handle(ctx: ContinuationContext, ex: Exception): RetryStrategy
}


/**
 * Simply keep retrying for ever and double the delay
 * on each attempt
 */
class RetryForEverExceptionStrategy(private val initialDelayMs: Long = 10) : ContinuationExceptionStrategy {
    override fun handle(
        ctx: ContinuationContext,
        ex: Exception
    ): RetryStrategy {
        val scheduledTime = System.currentTimeMillis() + (ctx.attempts * initialDelayMs)
        val newCtx = ctx.copy(attempts = ctx.attempts + 1)
        return DelayedRetry(newCtx, scheduledTime)
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