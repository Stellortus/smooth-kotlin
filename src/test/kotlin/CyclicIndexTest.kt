import top.stellortus.smooth_kotlin.utils.CyclicIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class CyclicIndexTest {
    @Test
    fun positive() {
        var index by CyclicIndex(5..7)
        assertEquals(5, index)
        index++
        assertEquals(6, index)
        index++
        assertEquals(7, index)
        index++
        assertEquals(5, index)
        index++
        assertEquals(6, index)
    }

    @Test
    fun negative() {
        var index by CyclicIndex(-5..-3)
        assertEquals(-5, index)
        index--
        assertEquals(-3, index)
        index++
        assertEquals(-5, index)
    }

    @Test
    fun reversed() {
        var index by CyclicIndex(7 downTo 5)
        assertEquals(5, index)
        index++
        assertEquals(6, index)
        index++
        assertEquals(7, index)
        index++
        assertEquals(5, index)
        index++
        assertEquals(6, index)
    }
}