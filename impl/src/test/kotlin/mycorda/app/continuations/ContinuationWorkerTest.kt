package mycorda.app.continuations

import org.junit.jupiter.api.Test

class ContinuationWorkerTest {
    @Test
    fun `should do something`() {
        val registry = SimpleContinuationRegistrar().register()
        val worker = ContinuableWorker(registry)




    }
}