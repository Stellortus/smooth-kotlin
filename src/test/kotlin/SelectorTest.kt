import top.stellortus.smooth_kotlin.selectors.CyclicSelector
import top.stellortus.smooth_kotlin.selectors.RandomSelector
import top.stellortus.smooth_kotlin.selectors.Selector
import top.stellortus.smooth_kotlin.selectors.ShuffledSelector
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectorTest {
    @Test
    fun cyclicReadDoesNotMove() {
        val selector = CyclicSelector(listOf("A", "B", "C"))
        val value by selector
        repeat(5) { assertEquals("A", value) }
        selector.stepForward()
        repeat(5) { assertEquals("B", value) }
    }

    @Test
    fun cyclicForwardAndBackwardWrapAround() {
        val selector = CyclicSelector(listOf("A", "B", "C"))
        val value by selector
        assertEquals(listOf("A", "B", "C", "A", "B", "C"), List(6) {
            val current = value
            selector.stepForward()
            current
        })
        assertEquals(listOf("C", "B", "A", "C", "B", "A"), List(6) {
            selector.stepBackward()
            value
        })
    }

    @Test
    fun cyclicCanChangeDirectionAcrossBoundary() {
        val selector = CyclicSelector(listOf("A", "B", "C"))
        val value by selector
        selector.stepBackward()
        assertEquals("C", value)
        selector.stepForward()
        assertEquals("A", value)
        selector.stepForward()
        assertEquals("B", value)
        selector.stepBackward()
        assertEquals("A", value)
    }

    @Test
    fun shuffledVisitsEachCandidateAndRepeatsOrder() {
        val candidates = listOf("A", "B", "C", "D")
        val selector = ShuffledSelector(candidates)
        val value by selector
        val initial = value
        repeat(5) { assertEquals(initial, value) }
        fun forwardRound() = List(candidates.size) {
            val current = value
            selector.stepForward()
            current
        }
        val firstRound = forwardRound()
        assertEquals(candidates.toSet(), firstRound.toSet())
        repeat(2) { assertEquals(firstRound, forwardRound()) }
        val backwardRound = List(candidates.size) {
            val current = value
            selector.stepBackward()
            current
        }
        assertEquals(listOf(firstRound.first()) + firstRound.drop(1).reversed(), backwardRound)
        assertEquals(initial, value)
    }

    @Test
    fun shuffledCanRetraceItsSteps() {
        val selector = ShuffledSelector(listOf("A", "B", "C", "D"))
        val value by selector
        val initial = value
        selector.stepForward()
        val next = value
        selector.stepBackward()
        assertEquals(initial, value)
        selector.stepForward()
        assertEquals(next, value)
    }

    @Test
    fun randomReadsAreStableAndMovementStaysWithinCandidates() {
        val candidates = listOf("A", "B", "C")
        val selector = RandomSelector(candidates)
        val value by selector
        repeat(100) {
            val current = value
            assertTrue(current in candidates)
            repeat(3) { assertEquals(current, value) }
            if (it % 3 == 0) selector.stepBackward() else selector.stepForward()
        }
    }

    @Test
    fun randomReplaysHistoryWithoutConsumingNewDraws() {
        val candidates = (0 until 100).toList()
        val oracle = Random(candidates.hashCode())
        val selector = RandomSelector(candidates)
        val value by selector
        val initial = oracle.nextInt(candidates.size)
        assertEquals(initial, value)
        selector.stepForward()
        val next = oracle.nextInt(candidates.size)
        assertEquals(next, value)
        selector.stepForward()
        val last = oracle.nextInt(candidates.size)
        assertEquals(last, value)
        selector.stepBackward()
        assertEquals(next, value)
        selector.stepBackward()
        assertEquals(initial, value)
        selector.stepBackward()
        val prepended = oracle.nextInt(candidates.size)
        assertEquals(prepended, value)
        assertEquals(listOf(prepended, initial, next, last), selector.history?.toList())
        for (expected in listOf(initial, next, last)) {
            selector.stepForward()
            assertEquals(expected, value)
        }
        selector.stepForward()
        val appended = oracle.nextInt(candidates.size)
        assertEquals(appended, value)
        assertEquals(listOf(prepended, initial, next, last, appended), selector.history?.toList())
    }

    @Test
    fun randomCapacityIncludesCurrentAndEvictsOppositeEnd() {
        val candidates = (0 until 100).toList()
        for (limit in listOf(1, 3)) {
            val selector = RandomSelector(candidates, memoryLimit = limit)
            val value by selector
            val oracle = Random(candidates.hashCode())
            val drawn = mutableListOf(oracle.nextInt(candidates.size))
            assertEquals(drawn.last(), value)
            repeat(20) {
                selector.stepForward()
                drawn.add(oracle.nextInt(candidates.size))
                assertEquals(drawn.last(), value)
                assertEquals(drawn.takeLast(limit), selector.history?.toList())
            }
            val retained = drawn.takeLast(limit)
            for (expected in retained.dropLast(1).reversed()) {
                selector.stepBackward()
                assertEquals(expected, value)
            }
            val expectedHistory = retained.toMutableList()
            repeat(20) {
                selector.stepBackward()
                val previous = oracle.nextInt(candidates.size)
                expectedHistory.removeAt(expectedHistory.lastIndex)
                expectedHistory.add(0, previous)
                assertEquals(previous, value)
                assertEquals(expectedHistory, selector.history?.toList())
            }
            for (expected in expectedHistory.drop(1)) {
                selector.stepForward()
                assertEquals(expected, value)
            }
            selector.stepForward()
            val appended = oracle.nextInt(candidates.size)
            assertEquals(appended, value)
            assertEquals(expectedHistory.drop(1) + appended, selector.history?.toList())
        }
    }

    @Test
    fun randomZeroCapacityDrawsOnEveryMoveWithoutHistory() {
        val candidates = (0 until 100).toList()
        val selector = RandomSelector(candidates, memoryLimit = 0)
        val value by selector
        val oracle = Random(candidates.hashCode())
        assertNull(selector.history)
        assertEquals(oracle.nextInt(candidates.size), value)
        repeat(100) {
            if (it % 2 == 0) selector.stepForward() else selector.stepBackward()
            val expected = oracle.nextInt(candidates.size)
            repeat(3) { assertEquals(expected, value) }
        }
    }

    @Test
    fun randomUnlimitedHistoryIsNotBoundedByCandidateCount() {
        val selector = RandomSelector(listOf("A", "B", "C"))
        val value by selector
        val recorded = mutableListOf(value)
        repeat(100) {
            selector.stepForward()
            recorded.add(value)
        }
        assertEquals(101, assertNotNull(selector.history).size)
        for (expected in recorded.dropLast(1).reversed()) {
            selector.stepBackward()
            assertEquals(expected, value)
        }
        for (expected in recorded.drop(1)) {
            selector.stepForward()
            assertEquals(expected, value)
        }
        assertEquals(101, selector.history.size)
    }

    @Test
    fun singleCandidateAndRepeatedDrawsAreSupported() {
        val selectors: List<Selector<String>> = listOf(
            CyclicSelector(listOf("only")),
            ShuffledSelector(listOf("only"))
        ) + listOf(-1, 0, 1, 3).map { RandomSelector(listOf("only"), memoryLimit = it) }
        for (selector in selectors) {
            val value by selector
            assertEquals("only", value)
            repeat(5) {
                selector.stepForward()
                assertEquals("only", value)
                selector.stepBackward()
                assertEquals("only", value)
            }
        }
        val repeated = RandomSelector(listOf("only"), memoryLimit = 3)
        repeat(5) { repeated.stepForward() }
        assertEquals(listOf(0, 0, 0), repeated.history?.toList())
    }

    @Test
    fun nullableCandidateIsSupported() {
        val selectors: List<Selector<String?>> = listOf(
            CyclicSelector<String?>(listOf(null)),
            ShuffledSelector<String?>(listOf(null))
        ) + listOf(-1, 0, 1, 3).map { RandomSelector<String?>(listOf(null), memoryLimit = it) }
        for (selector in selectors) {
            val value by selector
            assertNull(value)
            selector.stepForward()
            assertNull(value)
            selector.stepBackward()
            assertNull(value)
        }
    }

    @Test
    fun emptyCandidatesAreRejected() {
        assertFailsWith<IllegalArgumentException> { CyclicSelector(emptyList<Int>()) }
        assertFailsWith<IllegalArgumentException> { ShuffledSelector(emptyList<Int>()) }
        assertFailsWith<IllegalArgumentException> { RandomSelector(emptyList<Int>()) }
    }

    @Test
    fun randomInvalidCapacityIsRejected() {
        assertFailsWith<IllegalArgumentException> { RandomSelector(listOf(1), memoryLimit = -2) }
    }
}
