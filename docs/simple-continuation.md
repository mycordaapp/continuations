# Simple Continuation

[home](../README.md)

## Introduction

`SimpleContinuation` provides a complete implementation of `Continuation` and `Continuable`. While there are a number of
simplifying assumptions, the design is expected to be suitable for most production scenarios.

The following design decisions have been taken:

* all components are wired using the registry
* all state is stored in the [event store](https://github.com/mycordaapp/simple-event-store), and
  the [key-value store](https://github.com/mycordaapp/simple-kv-store).
* exceptions and retries and handled within the `SimpleContinuation`
* worker logic is implemented using a blocking pattern with a thread pool. Obviously this has implication on overall
  scalability and latency, but is felt to be more than adequate for all foreseeable usage patterns.

## Exceptions and Retries

This is essentially a case of wrapping `execBlock` in a handler. There is a prebuilt strategy built
around `ContinuationExceptionStrategy` and `RetryStategy`. The default handler explains it well

```kotlin
class RetryNTimesExceptionStrategy(
    private val maxRetries: Int = 10,
    private val initialDelayMs: Long = 10
) : ContinuationExceptionStrategy {
    override fun handle(
        ctx: ContinuationContext,
        ex: Exception
    ): RetryStrategy {
        val scheduledTime = System.currentTimeMillis() + ((ctx.attempts + 1) * initialDelayMs)
        return DelayedRetry(incrementRetriesHelper(ctx, ex), scheduledTime, maxRetries)
    }
}
```

