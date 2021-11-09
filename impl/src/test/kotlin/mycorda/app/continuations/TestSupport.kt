package mycorda.app.continuations

import mycorda.app.chaos.Chaos
import mycorda.app.helpers.random
import mycorda.app.registry.Registry
import mycorda.app.xunitpatterns.spy.Spy


class ThreeSteps(
    registry: Registry = Registry(),
    continuationKey: String = String.random()
) {
    // setup continuation
    private val factory = registry.geteOrElse(ContinuationFactory::class.java, SimpleContinuationFactory(registry))
    private val continuation = factory.get(continuationKey)

    // setup test support
    private val chaos = registry.geteOrElse(Chaos::class.java, Chaos(emptyMap(), true))
    private val spy = registry.geteOrElse(Spy::class.java, Spy())

    fun exec(startNumber: Int): Int {
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

    // only to control and observer the test double - wouldn't expect this in real code
    private fun testDecoration(step: String) {
        spy.spy(step)
        chaos.chaos(step)
    }
}