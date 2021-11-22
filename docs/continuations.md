# Continuation

[home](../README.md)

## What is a Continuation ?

A `Continuation` is simply an injectable service that allows the developer to break a method into 1 or more steps (code
blocks) that are managed by the `Continuation`. If the method is restarted then the Continuation logic will continue
directly after the last successful step. The breakdown of the steps and the logic within steps is entirely a decision
for developer, but in most cases Continuations will control processes that are subject to failure of some form.

The simple example below explains it well. In the case we use
the [Chaos](https://github.com/mycordaapp/commons/blob/master/docs/chaos.md) library to inject failure conditions.

```kotlin
class ThreeSteps(
    registry: Registry = SimpleContinuationRegistrar().register(),
    continuationId: ContinuationId = ContinuationId.random()
) : Continuable<Int, Int> {
    // #1. setup continuations
    private val factory = registry.get(ContinuationFactory::class.java)
    private val continuation = factory.get(continuationId)

    // #2. setup internal test support
    private val chaos = registry.getOrElse(Chaos::class.java, Chaos(emptyMap(), true))
    private val spy = registry.getOrElse(Spy::class.java, Spy())

    override fun exec(input: Int): Int {
        testDecoration("starting")
        // #3. run a sequence of calculations
        val step1Result = continuation.execBlock("step1", Int::class) {
            testDecoration("step1")
            input * input
        }
        val step2Result = continuation.execBlock("step2", Int::class) {
            testDecoration("step2")
            step1Result + 1
        }
        return continuation.execBlock("step3", Int::class) {
            testDecoration("step3")
            step2Result + step2Result
        }
    }

    // #4 control and spy on the test double - wouldn't expect this in real code
    private fun testDecoration(step: String) {
        spy.spy(step)
        chaos.chaos(step)
    }
}
```

* the `#1. setup continuations` block is more or less boiler-plate code. The class retrieves its `Continuation` via the
  factory. **The important point** is that for a given continuationId, the factory will return the state of the previous
  run, if there was run. The continuation doesn't need to know how the factory manages this. There is of course an
  implicit rule that only a single instance of a given continuation should be running at a point in time.
* the `#2. setup internal test support` and `#4 control and spy on the test double` blocks are purely for testing, and
  wouldn't be in production code.
* the `#3. run a sequence of calculations` block contains the sequence of continuations. The guarantee that the
  continuation will provide is that if restarted with the same `continuationId`, it will silently skip any completed
  steps, simply replacing the code block with the stored value from the earlier run. There is a limitation that the
  store value must conform to the rules
  of [Really Simple Serialisation(rss)](https://github.com/mycordaapp/really-simple-serialisation#readme).

The code snippets from the [Test Case](../impl/src/test/kotlin/mycorda/app/continuations/ContinuationScenarios.kt)
demonstrate the basic behaviour.

```kotlin
val registry = SimpleContinuationRegistrar().register()

@Test
fun `should run to completion`() {
    // Continuation provided by registry
    val result = ThreeSteps(registry).exec(10)
    assertThat(result, equalTo(202))
}

@Test
fun `should return result of previous run if rerun`() {
    // The basic promise of any continuation. If rerun
    // it will not trigger the block that have already completed
    val continuationId = ContinuationId.random()

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

```

## Running Continuations with retry / error handling

Continuations are required to solve two fundamental problems for stateful systems once we move beyond a single server /
container :

* what happens if a block fails? Should the code give up or retry? And, if it is retrying, what is the strategy for
  selecting delays and when to finally give up retrying
* what happens if the process running the continuation stops? Either due to a system failure or a restart issued by an
  orchestrator such as Kubernetes

The first problem can be handled either within the continuation logic (so the continuation itself does not fail)
or by treating the failure as a failure of the entire process. The first approach feels better and is supported by
the [Simple Continuation](./simple-continuation.md).

The second problem requires something that can be started / restarted and something to do the starting / restarting. The
ability to start is defined by the `Continuable` interface, and the ability to manage the starting is defined by
the `ContinuableWorker` interface.

## Continuable

## Serialisation

TODO - Some notes on rss

## Upgrades

_TODO - note on how to manage upgrades unlike Corda flows, this should be possible; there is no fancy serialisation
logic and upgrade specific behaviour can be injected_

_But need to work through the scenarios_