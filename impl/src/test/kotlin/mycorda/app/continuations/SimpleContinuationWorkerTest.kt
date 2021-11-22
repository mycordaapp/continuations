package mycorda.app.continuations

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import mycorda.app.chaos.AlwaysFail
import mycorda.app.chaos.Chaos
import mycorda.app.continuations.events.ContinuationCompletedFactory
import mycorda.app.continuations.events.ContinuationFailedFactory
import mycorda.app.continuations.events.ContinuationStartedFactory
import mycorda.app.continuations.simple.SimpleContinuableWorker
import mycorda.app.continuations.simple.SimpleContinuationRegistrar
import mycorda.app.registry.Registry
import mycorda.app.ses.*
import org.junit.jupiter.api.Test
import java.lang.RuntimeException

class SimpleContinuationWorkerTest {
    @Test
    fun `should run continuation via the worker`() {
        val (registry, es) = setupServices()
        val (worker, id, schedule) = scheduleContinuable(registry)

        // Start the worker and check the state machine
        assertThat(worker.status(id), equalTo(ContinuationStatus.UnknownContinuation))
        worker.schedule(schedule)
        assertThat(worker.status(id), equalTo(ContinuationStatus.NotStarted))
        // todo - a check that we also go into running state
        es.pollForEvent(
            AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationCompletedFactory.typeFilter()))
        )
        assertThat(worker.status(id), equalTo(ContinuationStatus.Completed))
        assertThat(worker.result<Int>(id), equalTo(202))
    }


    @Test
    fun `should run report exception for a failed continuation`() {
        val (registry, es) = setupServices { registry ->
            registry.store(Chaos(mapOf("step1" to listOf(AlwaysFail(RuntimeException("opps")))), true))
            registry.store(RetryNTimesExceptionStrategy(1))
        }

        val (worker, id, schedule) = scheduleContinuable(registry)

        // 3. Start the worker and check the state machine
        assertThat(worker.status(id), equalTo(ContinuationStatus.UnknownContinuation))
        worker.schedule(schedule)
        assertThat(worker.status(id), equalTo(ContinuationStatus.NotStarted))
        es.pollForEvent(
            AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationFailedFactory.typeFilter()))
        )
        assertThat(worker.status(id), equalTo(ContinuationStatus.Failed))
        assertThat(worker.exception(id), equalTo(ExceptionInfo(RuntimeException("opps"))))

        es.read(AggregateIdQuery(id.id())).forEach { println("${it.type} - ${it.payload}") }
    }

    @Test
    fun `should expose internal threading `() {
        val (registry, es) = setupServices()

        val (worker, id, schedule) = scheduleContinuable(registry)
        val monitor = worker as ContinuableWorkerThreadMonitor

        // Start the worker and check the threads
        assertThat({ monitor.threadId(id) }, throws<RuntimeException>())
        worker.schedule(schedule)
        es.pollForEvent(
            AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationStartedFactory.typeFilter()))
        )
        assert(monitor.threadId(id) != null)
    }

    private fun setupServices(beforeBlock: (registry: Registry) -> Unit = {}): Pair<Registry, EventStore> {
        val registry = Registry()
        beforeBlock.invoke(registry)
        SimpleContinuationRegistrar().register(registry)
        val es = registry.get(EventStore::class.java)
        val factory = registry.get(ContinuableFactory::class.java)
        factory.register(TestSupportRegistrations())
        factory.createInstance(ThreeSteps::class)  // force class loader cost BEFORE threads start as its slow
        return Pair(registry, es)
    }

    private fun scheduleContinuable(
        registry: Registry,
        startDelayMs: Long = 0
    ): Triple<ContinuableWorker, ContinuationId, Schedule<Int>> {
        val worker: ContinuableWorker = SimpleContinuableWorker(registry)
        val id = ContinuationId.random()
        val schedule = Schedule(
            ThreeSteps::class.qualifiedName!!,
            id,
            10,
            System.currentTimeMillis() + startDelayMs
        )
        return Triple(worker, id, schedule)
    }
}