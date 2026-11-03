import top.stellortus.smooth_kotlin.elseTake
import top.stellortus.smooth_kotlin.ifMeets
import top.stellortus.smooth_kotlin.thenTake
import kotlin.test.Test
import kotlin.test.assertEquals

class TakeTest {

    @Test
    fun takeTest() {
        assertEquals(1, 1.ifMeets { it > 1 }.thenTake { 2 })
        assertEquals(3, 1.ifMeets { it > 0 }.thenTake { 3 })
        assertEquals(4, (-1).ifMeets { it > 0 }.elseTake { 4 })
    }
}