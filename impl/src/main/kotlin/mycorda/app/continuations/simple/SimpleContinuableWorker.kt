package mycorda.app.continuations.simple

import mycorda.app.clock.PlatformTimer
import mycorda.app.continuations.*
import mycorda.app.continuations.events.*
import mycorda.app.registry.Registry
import mycorda.app.ses.AggregateIdQuery
import mycorda.app.ses.EventStore
import mycorda.app.ses.EventWriter
import mycorda.app.sks.SKS
import mycorda.app.sks.SKSValue
import mycorda.app.sks.SKSValueType
import mycorda.app.sks.SimpleKVStore
import mycorda.app.types.UniqueId
import java.lang.RuntimeException
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class SimpleContinuableWorker(registry: Registry) : ContinuableWorker {
    private val factory = registry.get(ContinuableFactory::class.java)
    private val schedule = ArrayList<Schedule<Any>>()
    private val executorService = Executors.newFixedThreadPool(10)
    private val es = registry.get(EventStore::class.java)
    private val kv = registry.get(SimpleKVStore::class.java)

    init {
        thread(start = true, block = monitorThread())
    }

    override fun <T> schedule(scheduled: Schedule<T>) {
        this.schedule(
            scheduled.continuableName,
            scheduled.id,
            scheduled.input,
            scheduled.time
        )
    }

    private fun <T> schedule(continuableName: String, id: ContinuationId, input: T, time: Long) {
        synchronized(this) {
            val payload = ScheduledActionCreated(
                key = id.id(),
                ctx = ContinuationContext(),
                clazzName = continuableName,
                scheduledTime = time
            )
            es.store(ScheduledActionCreatedFactory.create(payload))
            this.schedule.add(Schedule(continuableName, id, input as Any, time))
        }
    }

    override fun <O> result(id: ContinuationId): O {
        if (status(id) == ContinuationStatus.Completed) {
            return kv.getDeserialised<O>(UniqueId(id.id()))
        } else {
            throw RuntimeException("No result available")
        }
    }

    override fun exception(id: ContinuationId): ExceptionInfo {
        if (status(id) == ContinuationStatus.Failed) {
            return kv.getDeserialised(UniqueId(id.id()))
        } else {
            throw RuntimeException("No exception available")
        }
    }

    override fun status(id: ContinuationId): ContinuationStatus {
        var status = ContinuationStatus.UnknownContinuation
        val events = es.read(AggregateIdQuery(id.id()))
        if (events.isNotEmpty()) {
            status = ContinuationStatus.NotStarted

            events.forEach { ev ->
                if (ev.type == ContinuationStartedFactory.eventType() && status == ContinuationStatus.NotStarted) {
                    status = ContinuationStatus.Running
                }
                if (ev.type == ContinuationCompletedFactory.eventType() && status == ContinuationStatus.Running) {
                    status = ContinuationStatus.Completed
                }
                if (ev.type == ContinuationFailedFactory.eventType()) {
                    status = ContinuationStatus.Failed
                }
            }
        }
        return status
    }

    private fun monitorThread(): () -> Unit = {
        while (true) {
            synchronized(this) {
                val now = System.currentTimeMillis()
                val ready = schedule.filter { it.time <= now }

                ready.forEach {
                    val runnable: Runnable = WorkerThread(
                        factory,
                        es,
                        kv,
                        it.continuableName,
                        it.id,
                        it.input
                    )
                    executorService.submit(runnable)
                }
                schedule.removeAll(ready)
            }
            PlatformTimer.sleepForTicks(1)
        }
    }
}

class WorkerThread(
    private val factory: ContinuableFactory,
    private val ew: EventWriter,
    private val kv: SKS,
    private val continuable: String,
    private val id: ContinuationId,
    private val input: Any
) :
    Runnable {
    override fun run() {
        try {
            Thread.currentThread().name = "WorkerThread - $continuable - $id";
            val continuation = factory.createInstance<Any, Any>(continuable, id)
            ew.store(ContinuationStartedFactory.create(id))
            val result = continuation.exec(input)
            kv.put(UniqueId(id.id()), SKSValue(result, SKSValueType.Serialisable))
            ew.store(ContinuationCompletedFactory.create(id))
        } catch (ie: InterruptedException) {
            // should we have custom logic for the thread problems ?
            kv.put(UniqueId(id.id()), SKSValue(ExceptionInfo(ie), SKSValueType.Serialisable))
            ew.store(ContinuationFailedFactory.create(id, ie))
        } catch (ex: Exception) {
            kv.put(UniqueId(id.id()), SKSValue(ExceptionInfo(ex), SKSValueType.Serialisable))
            ew.store(ContinuationFailedFactory.create(id, ex))
        } catch (t: Throwable) {
            kv.put(UniqueId(id.id()), SKSValue(ExceptionInfo(t), SKSValueType.Serialisable))
            ew.store(ContinuationFailedFactory.create(id, t))
        }
    }

}