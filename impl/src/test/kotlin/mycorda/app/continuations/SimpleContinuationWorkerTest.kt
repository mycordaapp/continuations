package mycorda.app.continuations

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import mycorda.app.chaos.AlwaysFail
import mycorda.app.chaos.Chaos
import mycorda.app.chaos.DelayUptoNTicks
import mycorda.app.clock.PlatformTick
import mycorda.app.clock.PlatformTimer
import mycorda.app.continuations.events.ContinuationCompletedFactory
import mycorda.app.continuations.events.ContinuationFailedFactory
import mycorda.app.continuations.events.ContinuationStartedFactory
import mycorda.app.continuations.simple.SimpleContinuableWorker
import mycorda.app.continuations.simple.SimpleContinuationRegistrar
import mycorda.app.registry.Registry
import mycorda.app.ses.*
import mycorda.app.types.ExceptionInfo
import org.junit.Ignore
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.lang.RuntimeException

class SimpleContinuationWorkerTest {

    @Test
    fun `should run continuation via the worker`() {
        val (registry, es) = setupServices()
        val (worker, id, schedule) = scheduleContinuable(registry, PlatformTimer.clockTick() + 10)

        // Start the worker and check the state machine
        assertThat(worker.status(id), equalTo(ContinuationStatus.UnknownContinuation))
        assertThat(worker.continuations().toList(), isEmpty)
        worker.schedule(schedule)
        assertThat(worker.status(id), equalTo(ContinuationStatus.NotStarted))
        assertThat(worker.continuations().toList(), equalTo(listOf(id)))

        // keep polling until we trigger running state
        var tries = 0
        while (worker.status(id) != ContinuationStatus.Running && tries < 20) {
            PlatformTimer.sleepForTicks(1)
            tries++
        }

        // wait for completed event
        es.pollForEvent(
            AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationCompletedFactory.typeFilter()))
        )
        assertThat(worker.status(id), equalTo(ContinuationStatus.Completed))
        assertThat(worker.result<Int>(id), equalTo(202))
        assertThat(worker.continuations().toList(), equalTo(listOf(id)))
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
    }

    @Test
    fun `should expose internal threading`() {
        val (registry, es) = setupServices()

        val (worker, id, schedule) = scheduleContinuable(registry)
        val monitor = worker as ContinuableWorkerThreadMonitor

        // Start the worker and check the threads
        assertThat({ monitor.threadId(id) }, throws<RuntimeException>())
        worker.schedule(schedule)
        es.pollForEvent(
            AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationStartedFactory.typeFilter()))
        )
        assert(monitor.threadId(id) != 0L)
    }

    @Test
    @Disabled
    fun `should recover on restart`() {
        // This test simulates a process restart by killing a thread while
        // the continuation is running. We then restart the continuation
        // and check it runs successfully until completion. Finally we test
        // the events emitted to confirm they meet our expectations

        val (registry, es) = setupServices { registry ->
            registry.store(
                Chaos(
                    mapOf(
                        "step1" to listOf(
                            // inject enough delay to ensure we kill the thread BEFORE
                            // the continuation has has a chance to complete
                            DelayUptoNTicks(
                                minDelay = PlatformTick.of(3),
                                maxDelay = PlatformTick.of(5)
                            )
                        )
                    ), true
                )
            )
            //registry.store(FailImmediatelyExceptionStrategy())
        }

        val (worker, id, schedule) = scheduleContinuable(registry)
        val monitor = worker as ContinuableWorkerThreadMonitor

        // Start the worker
        assertThat({ monitor.threadId(id) }, throws<RuntimeException>())
        worker.schedule(schedule)
        es.pollForEvent(
            AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationStartedFactory.typeFilter()))
        )

        // kill the thread
        val threadId = monitor.threadId(id)
        Thread.getAllStackTraces().keys.forEach {
            if (it.id == threadId) {
               it.interrupt()
            }
        }
        // should stay in running state even if we have given the
        // original thread time to complete
        //assertThat(worker.status(id), equalTo(ContinuationStatus.Running))
        Thread.sleep(1000)   // Platform Timer doesn't seem to be working reliably here

        println("all events")
        es.read(EverythingQuery).forEach {
            println(it.type)
        }
        assertThat(worker.status(id), equalTo(ContinuationStatus.Running))


        //assert(monitor.threadId(id) != 0L)
    }


    private fun setupServices(beforeBlock: (registry: Registry) -> Unit = {}): Pair<Registry, EventStore> {
        val registry = Registry()
        beforeBlock.invoke(registry)
        SimpleContinuationRegistrar().register(registry)
        val es = registry.get(EventStore::class.java)
        val factory = registry.get(ContinuableFactory::class.java)
        factory.register(TestSupportRegistrations())
        factory.createInstance(ThreeSteps::class)  // force class loader cost BEFORE threads start as it is slow
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