package mycorda.app.continuations

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import mycorda.app.chaos.AlwaysFail
import mycorda.app.chaos.Chaos
import mycorda.app.continuations.events.ContinuationCompletedFactory
import mycorda.app.continuations.events.ContinuationFailedFactory
import mycorda.app.continuations.simple.SimpleContinuableWorker
import mycorda.app.continuations.simple.SimpleContinuationRegistrar
import mycorda.app.registry.Registry
import mycorda.app.ses.*
import org.junit.jupiter.api.Test
import java.lang.RuntimeException

class SimpleContinuationWorkerTest {
    @Test
    fun `should run continuation via the worker`() {
        // 1. setup registry & factory
        val registry = SimpleContinuationRegistrar().register()
        val es = registry.get(EventStore::class.java)
        val factory = registry.get(ContinuableFactory::class.java)
        factory.register(TestSupportRegistrations())
        factory.createInstance(ThreeSteps::class)  // force class loader cost BEFORE test

        // 2. setup a new schedule
        val worker: ContinuableWorker = SimpleContinuableWorker(registry)
        val id = ContinuationId.random()
        val schedule = Schedule(ThreeSteps::class.qualifiedName!!, id, 10, System.currentTimeMillis() + 50)

        // 3. Start the worker and check the state machine
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
        // 1. setup registry & factory
        val registry = Registry()
        registry.store(Chaos(mapOf("step1" to listOf(AlwaysFail(RuntimeException("opps")))), true))
        registry.store(RetryNTimesExceptionStrategy(1))
        SimpleContinuationRegistrar().register(registry)
        val es = registry.get(EventStore::class.java)
        val factory = registry.get(ContinuableFactory::class.java)
        factory.register(TestSupportRegistrations())

        factory.createInstance(ThreeSteps::class)  // force class loader cost BEFORE test

        // 2. setup a new schedule
        val worker: ContinuableWorker = SimpleContinuableWorker(registry)
        val id = ContinuationId.random()
        val schedule = Schedule(ThreeSteps::class.qualifiedName!!, id, 10, System.currentTimeMillis())

        // 3. Start the worker and check the state machine
        assertThat(worker.status(id), equalTo(ContinuationStatus.UnknownContinuation))
        worker.schedule(schedule)
        assertThat(worker.status(id), equalTo(ContinuationStatus.NotStarted))
        es.pollForEvent(
            AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationFailedFactory.typeFilter()))
        )
        assertThat(worker.status(id), equalTo(ContinuationStatus.Failed))
        assertThat(worker.exception(id), equalTo(ExceptionInfo(RuntimeException("opps"))))
    }

}