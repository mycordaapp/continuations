package mycorda.app.continuations

/**
 * The core concept. A `Continuable` is simply a
 * standard pattern for scheduling and restarting `Continuations`. Its not mandatory
 * to use the the Continuable pattern with a Continuable, but without it a similar
 * concept would be probably be required in order to build a robust application.
 *
 * An implementation of `Continuable` *MUST* conform to the rules of `ContinuableFactory` in order
 * to make use of the higher level services. This rule is simple, it must have a constructor that takes
 * a Registry and a ContinuationId as parameters, e.g.
 **
 * <pre>
 *  class MyContinuable (private val registry: Registry,
 *                       private val continuationId: ContinuationId) : Continuable<String,String> {
 *
 *  // implementation
 * }
 * </pre>
 *
 */
interface Continuable<I, O> {
    fun exec(input: I): O
}
