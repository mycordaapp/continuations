package mycorda.app.continuations

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import mycorda.app.chaos.AlwaysFail
import mycorda.app.chaos.Chaos
import mycorda.app.continuations.events.ScheduledActionCreatedFactory
import mycorda.app.helpers.random
import mycorda.app.registry.Registry
import mycorda.app.ses.EventStore
import mycorda.app.ses.FileEventStore
import mycorda.app.xunitpatterns.spy.Spy
import org.junit.jupiter.api.Test

class SchedulerScenarios {

//    @Test
//    fun `it should do something`() {
//        val registry = Registry().store(FileEventStore("../.testing/${String.random()}"))
//        SimpleContinuationRegistrar().register(registry)
//
//        val continuation = SimpleContinuation(registry)
//        val ctx = ContinuationContext()
//        val block: (ctx: ContinuationContext) -> Int = {
//            println(it)
//            it.attempts * 10
//        }
//
//        val result = continuation.execBlock("step1", 1::class, block, ctx)
//        assertThat(result, equalTo(0))
//
//        val scheduled = Scheduled<Int>("step1", ctx, Int::class, block, System.currentTimeMillis())
//        val ev = ScheduledActionCreatedFactory.create(scheduled)
//        val es = registry.get(EventStore::class.java)
//        es.store(ev)
//    }


//    @Test
//    fun `foo`() {
//        val registry = Registry().store(FileEventStore("../.testing/${String.random()}"))
//        registry.store(FailImmediatelyExceptionStrategy())
//        SimpleContinuationRegistrar().register(registry)
//
//        val continuationId = ContinuationId.random()
//
//        val chaos = Chaos(
//            mapOf(
//                "step2" to listOf(AlwaysFail()),
//            ),
//            ignoreWarnings = true
//        )
//        val spy = Spy()
//
//
//        val result = ThreeSteps(registry.clone().store(spy).store(chaos), continuationId).exec(10)
//        assertThat(result, equalTo(202))
//
//    }
}