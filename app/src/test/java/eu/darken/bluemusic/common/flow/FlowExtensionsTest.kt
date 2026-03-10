package eu.darken.bluemusic.common.flow

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FlowExtensionsTest : BaseTest() {

    // region withPrevious

    @Test
    fun `withPrevious - first emission has null previous`() = runTest {
        val result = flowOf(1).withPrevious().toList()
        result shouldBe listOf(null to 1)
    }

    @Test
    fun `withPrevious - multiple emissions pair correctly`() = runTest {
        val result = flowOf(1, 2, 3).withPrevious().toList()
        result shouldBe listOf(
            null to 1,
            1 to 2,
            2 to 3,
        )
    }

    @Test
    fun `withPrevious - empty flow emits nothing`() = runTest {
        val result = emptyFlow<Int>().withPrevious().toList()
        result shouldBe emptyList()
    }

    // endregion

    // region takeUntilAfter

    @Test
    fun `takeUntilAfter - includes matching element then stops`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5)
            .takeUntilAfter { it == 3 }
            .toList()
        result shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `takeUntilAfter - emits all when no match`() = runTest {
        val result = flowOf(1, 2, 3)
            .takeUntilAfter { it == 99 }
            .toList()
        result shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `takeUntilAfter - stops at first element if it matches`() = runTest {
        val result = flowOf(1, 2, 3)
            .takeUntilAfter { it == 1 }
            .toList()
        result shouldBe listOf(1)
    }

    // endregion
}
