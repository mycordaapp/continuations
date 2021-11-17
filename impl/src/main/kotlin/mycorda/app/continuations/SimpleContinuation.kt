package mycorda.app.continuations

import mycorda.app.continuations.events.ScheduledActionCreatedFactory
import mycorda.app.registry.Registrar
import mycorda.app.registry.Registry
import mycorda.app.ses.EventStore
import mycorda.app.ses.SimpleEventStore
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
                registry.store(SimpleEventStore())
            }
        }
        registry.store(SimpleSchedulerService(registry))
        registry.store(SimpleSchedulerFactory(registry))
        registry.store(SimpleContinuationFactory(registry))
        return registry
    }
}

class SimpleScheduler(
    registry: Registry,
    private val continuation: Continuation
) : Scheduler {
    private val schedulerService = registry.get(SchedulerService::class.java)
    override fun <T : Any> schedule(scheduled: Scheduled<T>) {
        schedulerService.schedule(scheduled)
    }

    override fun <T : Any> waitFor(key: String): T {
        val scheduled = schedulerService.get<T>(key)
        val delayedRetry = max(scheduled.scheduledTime - System.currentTimeMillis(), 1)
        Thread.sleep(delayedRetry)
        schedulerService.completed(scheduled.key)
        return continuation.execBlock(key, scheduled.clazz, scheduled.block, scheduled.ctx) as T
    }
}

class SimpleSchedulerFactory(private val registry: Registry) : SchedulerFactory {
    override fun get(continuation: Continuation): Scheduler {
        return SimpleScheduler(registry, continuation)
    }
}


interface SchedulerService {
    fun <T : Any> schedule(action: Scheduled<T>)

    fun <T : Any> get(key: String): Scheduled<T>

    fun completed(key: String)
}


class SimpleSchedulerService(registry: Registry) : SchedulerService {
    private val es = registry.get(EventStore::class.java)
    private val schedules = ArrayList<Scheduled<Any>>()

    override fun <T : Any> schedule(action: Scheduled<out T>) {
        // create a persistent event for recovery
        es.store(ScheduledActionCreatedFactory.create(action))
        schedules.add(action)
    }

    override fun <T : Any> get(key: String): Scheduled<T> {
        return schedules.single { it.key == key } as Scheduled<T>
    }

    override fun completed(key: String) {
        schedules.removeIf { it.key == key }
    }

}


class SimpleContinuation(
    private val exceptionStrategy: ContinuationExceptionStrategy = RetryNTimesExceptionStrategy(),
    schedulerFactory: SchedulerFactory
) : Continuation {
    constructor(registry: Registry) : this(
        registry.getOrElse(ContinuationExceptionStrategy::class.java, RetryNTimesExceptionStrategy()),
        registry.get(SchedulerFactory::class.java)
    )

    private val scheduler = schedulerFactory.get(this)
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
                        println("opps")
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
class SimpleContinuationFactory(registry: Registry) : ContinuationFactory {
    private val registry = registry.clone() // make a clean copy as registry is mutable
    private val schedulerFactory = registry.get(SchedulerFactory::class.java)
    private val lookup = HashMap<ContinuationId, SimpleContinuation>()
    override fun get(continuationId: ContinuationId): Continuation {
        lookup.putIfAbsent(continuationId, SimpleContinuation(exceptionStrategy(), schedulerFactory))
        return lookup[continuationId]!!
    }

    override fun exceptionStrategy(): ContinuationExceptionStrategy {
        return registry.getOrElse(ContinuationExceptionStrategy::class.java, RetryNTimesExceptionStrategy())
    }
}
