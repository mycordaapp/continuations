# The "Continuations" framework

[home](../README.md)

## What it does

A [continuations](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines/-continuation/) style concept but
designed to operate at cloud scale. So we resume a process, somewhere on the cloud.

While this is written to support [Tasks](https://github.com/mycordaapp/tasks) the concept is sufficiently simple and
standalone that it can have its own repo.

## Rationale

This library solves some basic problems with distributed computing:

- restarting
    - checkpointing (where was the process last time)
    - workers (where should this be run on restart)
- retrying
    - what to do if there is an exception?
    - when to retry (scheduling)
    - when to give up (dead letter)
- health
    - passive monitoring (e.g. probes)
    - built in, active monitoring (e.g. heart beats)
    - when to restart (scheduling)
    - when to give up (dead letter)

## Basic Principles

The three core interfaces are [Continuation](./impl/src/main/kotlin/mycorda/app/continuations/Continuation.kt),
[Continuable](./impl/src/main/kotlin/mycorda/app/continuations/Continuable.kt)
and [Continuable Worker](./impl/src/main/kotlin/mycorda/app/continuations/ContinuableWorker.kt).

The application logic is implemented as a `Continuation`. The ability to start and restart is implemented
by `Continuable`. The ability to schedule , check progress and get the result of a `Continuation` is implemented by
the `ContinuationWorker` interface.

See the [docs](./docs/index.md) for details.

## Next Steps

_todo_

## Dependencies

As with everything in [myCorda dot App](https://mycorda.app), this library has minimal dependencies:

* Kotlin 1.4
* Java 11
* The object [Registry](https://github.com/mycordaapp/registry#readme)
* The [Commons](https://github.com/mycordaapp/commons#readme) module
* The [Really Simple Serialisation(rss)](https://github.com/mycordaapp/really-simple-serialisation#readme) module
    - [Jackson](https://github.com/FasterXML/jackson) for JSON and YAML serialisation
* [Simple Event Store](https://github.com/mycordaapp/simple-event-store) and
  [Simple KV Store](https://github.com/mycordaapp/simple-kv-store) if using the
  inbuilt [Simple Continuation](./impl/src/main/kotlin/mycorda/app/continuations/simple/SimpleContinuation.kt)

