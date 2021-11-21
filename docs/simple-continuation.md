# Simple Continuation 

[home](../README.md)

TODO - describe the SimpleContinuation logic 


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

_TODO - show how this is wired up_