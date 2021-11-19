package mycorda.app.continuations

interface Continuable<I, O> {
    fun exec(input: I): O
}
