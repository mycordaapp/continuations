package mycorda.app.continuations

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test


class ContinuationScenarios {

    @Test
    fun `should complete normally with inbuilt Continuation`() {
        // using inbuilt SimpleContinuationFactory
        val result = ThreeSteps().exec(10)
        assertThat(result, equalTo(202))
    }

}