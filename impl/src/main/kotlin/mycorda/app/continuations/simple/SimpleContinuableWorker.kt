package mycorda.app.continuations.simple

import mycorda.app.clock.PlatformTimer
import mycorda.app.continuations.*
import mycorda.app.continuations.events.*
import mycorda.app.registry.Registry
import mycorda.app.ses.*
import mycorda.app.sks.SKS
import mycorda.app.sks.SKSValue
import mycorda.app.sks.SKSValueType
import mycorda.app.sks.SimpleKVStore
import mycorda.app.types.UniqueId
import java.lang.RuntimeException
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class SimpleContinuableWorker(registry: Registry) : ContinuableWorker, ContinuableWorkerThreadMonitor {
    private val factory = registry.get(ContinuableFactory::class.java)
    private val schedule = ArrayList<Schedule<Any>>()
    private val es = registry.get(EventStore::class.java)
    private val kv = registry.get(SimpleKVStore::class.java)
    private val executorService = Executors.newFixedThreadPool(10)

    init {
        thread(start = true, block = monitorThread())
    }

    override fun startup() {
        // todo - thread startup should be within this method for cleaner
        //        recovery

        continuations().forEach {
            if (status(it) == ContinuationStatus.Running) {
                val ev =
                    es.read(AllOfQuery(listOf(AggregateIdQuery(it.id()), ScheduledActionCreatedFactory.typeFilter())))
                        .single()

                val input = kv.get(UniqueId(it.toString() + ":input"))

                val payload = ev.payload as ScheduledActionCreated
                this.schedule.add(
                    Schedule(
                        payload.clazzName, it,
                        input.value, System.currentTimeMillis()
                    )
                )
            }
        }

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
            return kv.getDeserialised(UniqueId(id.id()))
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

    override fun continuations(): Iterable<ContinuationId> {
        val pattern = LikeString("mycorda.app.continuations.events.%")
        return es.read(LikeEventTypeQuery(pattern))
            .map { it.aggregateId }
            .distinct()
            .map { ContinuationId.fromString(it!!) }
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

    override fun threadId(id: ContinuationId): Long {
        val events = es.read(AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationStartedFactory.typeFilter())))
        if (events.isNotEmpty()) {
            return (events.last().payload as ContinuationStarted).threadId
        }
        throw RuntimeException("No threadId available for ContinuationId: $id")
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
            kv.put(UniqueId(id.id() + ":input"), SKSValue(input, SKSValueType.Serialisable))
            ew.store(ContinuationStartedFactory.create(ContinuationStarted(id, Thread.currentThread().id)))
            val result = continuation.exec(input)
            kv.put(UniqueId(id.id()), SKSValue(result, SKSValueType.Serialisable))
            ew.store(ContinuationCompletedFactory.create(id))
        } catch (ie: InterruptedException) {
            // todo - we should be pushing these back in for reprocessing

            //kv.put(UniqueId(id.id()), SKSValue(ExceptionInfo(ie), SKSValueType.Serialisable))
            //ew.store(ContinuationFailedFactory.create(id, ie))
        } catch (ex: Exception) {
            kv.put(UniqueId(id.id()), SKSValue(ExceptionInfo(ex), SKSValueType.Serialisable))
            ew.store(ContinuationFailedFactory.create(id, ex))
        } catch (t: Throwable) {
            kv.put(UniqueId(id.id()), SKSValue(ExceptionInfo(t), SKSValueType.Serialisable))
            ew.store(ContinuationFailedFactory.create(id, t))
        }
    }
}