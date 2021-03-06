package mycorda.app.continuations

import com.google.common.base.Predicates.instanceOf
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import mycorda.app.continuations.simple.SimpleContinuationRegistrar
import org.junit.jupiter.api.Test

class ContinuableFactoryTest {

    @Test
    fun `should register and create`() {
        val registry = SimpleContinuationRegistrar().register()
        val factory = ContinuableFactory(registry)

        factory.register(ContinuableRegistration(ThreeSteps::class))

        val instance = factory.createInstance<Int, Int>("mycorda.app.continuations.ThreeSteps")
        assert(instance is ThreeSteps)

        assertThat(instance.exec(10), equalTo(202))
    }
}