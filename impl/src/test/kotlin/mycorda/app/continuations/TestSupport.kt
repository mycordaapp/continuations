package mycorda.app.continuations

import mycorda.app.chaos.Chaos
import mycorda.app.helpers.random
import mycorda.app.registry.Registry
import mycorda.app.xunitpatterns.spy.Spy


class ThreeSteps(
    registry: Registry = SimpleContinuationRegistrar().register(),
    continuationKey: String = String.random()
) {
    // setup continuations
    private val factory = registry.getOrElse(ContinuationFactory::class.java, SimpleContinuationFactory(registry))

    private val continuation = factory.get(continuationKey)

    // setup internal test support
    private val chaos = registry.getOrElse(Chaos::class.java, Chaos(emptyMap(), true))
    private val spy = registry.getOrElse(Spy::class.java, Spy())

    fun exec(startNumber: Int): Int {
        testDecoration("starting")
        // run a sequence of calculations
        val step1Result = continuation.execBlock("step1", 1::class) {
            testDecoration("step1")
            startNumber * startNumber
        }
        val step2Result = continuation.execBlock("step2", 1::class) {
            testDecoration("step2")
            step1Result + 1
        }
        return continuation.execBlock("step3", 1::class) {
            testDecoration("step3")
            step2Result + step2Result
        }
    }

    // only to control and spy on the test double - wouldn't expect this in real code
    private fun testDecoration(step: String) {
        spy.spy(step)
        chaos.chaos(step)
    }
}