package mycorda.app.continuations

import mycorda.app.clock.PlatformTimer
import mycorda.app.continuations.events.ContinuationCompletedFactory
import mycorda.app.ses.*
import org.junit.jupiter.api.Test

class ContinuationWorkerTest {
    @Test
    fun `should do something`() {
        val registry = SimpleContinuationRegistrar().register()
        val es = registry.get(EventStore::class.java)

        val worker = ContinuableWorker(registry)

        val factory = registry.get(ContinuableFactory::class.java)
        factory.register(ThreeSteps::class)
        // force class loader cost BEFORE test
        factory.createInstance(ThreeSteps::class, ContinuationId.random())


        val id = ContinuationId.random()
        worker.schedule(ThreeSteps::class.qualifiedName!!, id, 10, System.currentTimeMillis() + 50)
        //Thread.sleep(200)


        waitForEvent(
            es, AllOfQuery(listOf(AggregateIdQuery(id.id()), ContinuationCompletedFactory.typeFilter()))
        )

        //Thread.sleep(1000)
        println("status 4 - ${worker.status(id)}")

        registry.get(EventStore::class.java).read(EverythingQuery).forEach {
            println(it)
        }

        Thread.sleep(100)
        println(worker.result<Int>(id))
    }

    fun waitForEvent(es: EventReader, query: EventQuery) {
        while (true) {
            if (es.read(query).isNotEmpty()) return
            PlatformTimer.sleepForTicks(1)
        }
    }
}