# Design Notes
[home](../README.md)

## What is a Continuation

A `Continuation` is simply an injectable service that allows the developer to break a method into 1 or more steps that
are managed by the `Continuation`. If the method is restarted then the Continuation logic will restart directly after
the last successful step. The breakdown of the steps and the logic within steps is entirely a decision for developer,
but in most cases Continuations will control processes that are subject to failure of some form.

The simple example below explains it well. In the case we use
the [Chaos](https://github.com/mycordaapp/commons/blob/master/docs/chaos.md) library to inject failure conditions.

```kotlin

class ThreeSteps(
    registry: Registry = SimpleContinuationRegistrar().register(),
    continuationKey: String = String.random()
) {
    // setup continuations
    private val factory = registry.get(ContinuationFactory::class.java)
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
```

Taking a simple example, when `step 1` is completed it results are stored in key-value store and fact that the step has
completed is also stored. So in the event of a restart the associated block

```kotlin
continuation.execBlock("step1", 1::class) {
    testDecoration("step1")
    startNumber * startNumber
}
```

will be skipped and replaced to the stored value.

Any data stored has the restriction that it must be serialisable
by [Really Simple Serialisation(rss)](https://github.com/mycordaapp/really-simple-serialisation). The same restrictions
is also applied to [Tasks](https://github.com/mycordaapp/tasks/blob/master/README.md).

## Serialisation

TODO - Some notes on rss

## Upgrades

TODO - note on how to manage upgrades unlike Corda flows, this should be possible , but need to work through the
scenarios 