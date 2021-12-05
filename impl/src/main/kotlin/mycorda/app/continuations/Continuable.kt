package mycorda.app.continuations

import mycorda.app.types.ExceptionInfo

/**
 * The core concept. A `Continuable` is simply a
 * standard pattern for scheduling and restarting `Continuations`. Its not mandatory
 * to use the the Continuable pattern with a Continuable, but without it a similar
 * concept would be probably be required in order to build a robust application.
 *
 * An implementation of `Continuable` must have a constructor that takes
 * a Registry and a ContinuationId as parameters, e.g.
 *
 * The single entry point is the `exec` method.
 *
 * <pre>
 *  class MyContinuable (private val registry: Registry,
 *                       private val continuationId: ContinuationId) : Continuable<String,String> {
 *
 *  // implementation
 * }
 * </pre>
 */
interface Continuable<I, O> {
    fun exec(input: I): O
}
