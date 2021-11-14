package mycorda.app.continuations

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import mycorda.app.chaos.Chaos
import mycorda.app.chaos.FailWithPattern
import mycorda.app.chaos.Noop
import mycorda.app.helpers.random
import mycorda.app.xunitpatterns.spy.Spy
import org.junit.jupiter.api.Test

class ContinuationScenarios {

    @Test
    fun `should complete normally with inbuilt Continuation`() {
        // using inbuilt Factories
        val result = ThreeSteps().exec(10)
        assertThat(result, equalTo(202))
    }

    @Test
    fun `should wire up Continuation using registrar`() {
        val registry = SimpleContinuationRegistrar().register()

        // Continuation provided by registry
        val result = ThreeSteps(registry).exec(10)
        assertThat(result, equalTo(202))
    }

    @Test
    fun `should return result of previous run if rerun `() {
        // The basic promise of any continuation. If rerun
        // it will not trigger the block that have already completed
        val registry = SimpleContinuationRegistrar().register()
        val continuationId = String.random()

        // Spy on the first run
        val spy1 = Spy()
        val result1 = ThreeSteps(registry.clone().store(spy1), continuationId).exec(10)
        assertThat(result1, equalTo(202))
        assertThat(spy1.secrets(), equalTo(listOf("starting", "step1", "step2", "step3")))

        // Spy on the second run - all steps have completed, so it just returns the result of step3
        val spy2 = Spy()
        val result2 = ThreeSteps(registry.clone().store(spy2), continuationId).exec(10)
        assertThat(result2, equalTo(202))
        assertThat(spy2.secrets(), equalTo(listOf("starting")))
    }

    @Test
    fun `should retry failed steps using retry strategy`() {
        val registry = SimpleContinuationRegistrar().register()
        val continuationId = String.random()

        val chaos = Chaos(
            mapOf(
                "step1" to listOf(Noop()),
                "step2" to listOf(FailWithPattern("FFF.")),
                "step3" to listOf(Noop()),
            )
        )
        val spy = Spy()

        // Run with some chaos - step2 will fail 3 times
        // note, by default, SimpleContinuation will retry after an exception upto 10 times
        val result = ThreeSteps(registry.clone().store(spy).store(chaos), continuationId).exec(10)
        assertThat(result, equalTo(202))

        // step2 is present 4 times - three failures and one success
        assertThat(spy.secrets(), equalTo(listOf("starting", "step1", "step2", "step2", "step2", "step2", "step3")))

    }


}