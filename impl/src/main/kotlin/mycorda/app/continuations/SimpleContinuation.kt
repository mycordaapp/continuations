package mycorda.app.continuations

import mycorda.app.registry.Registrar
import mycorda.app.registry.Registry
import mycorda.app.ses.EventStore
import java.lang.Exception
import java.lang.Long.max
import java.lang.RuntimeException
import kotlin.reflect.KClass


/**
 * Wires up a SimpleContinuation
 */
class SimpleContinuationRegistrar : Registrar {
    override fun register(registry: Registry, strict: Boolean): Registry {
        if (!registry.contains(EventStore::class.java)) {
            if (strict) {
                throw RuntimeException("There should be an EventStore class in the registry")
            } else {
                registry.store(EventStore::class.java)
            }
        }
        //registry.store(SimpleScheduler(registry))
        registry.store(SimpleContinuationFactory(registry))
        return registry
    }
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
    registry: Registry
) : Continuation {
    private val scheduler = registry.getOrElse(Scheduler::class.java,SimpleScheduler(this))
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
        return registry.getOrElse(ContinuationExceptionStrategy::class.java, RetryNTimesExceptionStrategy())
    }
}
