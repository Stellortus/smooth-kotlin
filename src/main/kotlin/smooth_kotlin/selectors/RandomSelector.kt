package top.stellortus.smooth_kotlin.selectors

import top.stellortus.smooth_kotlin.annotations.Api
import kotlin.random.Random
import kotlin.reflect.KProperty

/** [memoryLimit] limits the total records including the current one; -1 is unlimited, 0 disables history. */
@Api
class RandomSelector<T>(values: List<T>, val memoryLimit: Int = -1) : Selector<T>(values) {

    init {
        require(memoryLimit >= -1) { "memoryLimit must be -1 or non-negative" }
    }

    val randomSource = Random(values.hashCode())

    private var currentIndex = newRandom()

    val history: ArrayDeque<Int>? = if (memoryLimit != 0) {
        ArrayDeque<Int>().apply { addLast(currentIndex) }
    } else null

    private var historyPosition = 0

    fun newRandom() = randomSource.nextInt(values.size)

    override fun stepForward() {
        if (history == null) {
            currentIndex = newRandom()
            return
        }

        if (historyPosition < history.lastIndex) {
            historyPosition++
            currentIndex = history[historyPosition]
            return
        }

        val nextIndex = newRandom()
        if (history.size.toUInt() >= memoryLimit.toUInt()) {
            history.removeFirst()
        }
        history.addLast(nextIndex)
        historyPosition = history.lastIndex
        currentIndex = nextIndex
    }

    override fun stepBackward() {
        val history = history
        if (history == null) {
            currentIndex = newRandom()
            return
        }

        if (historyPosition > 0) {
            historyPosition--
            currentIndex = history[historyPosition]
            return
        }

        val previousIndex = newRandom()
        if (history.size.toUInt() >= memoryLimit.toUInt()) {
            history.removeLast()
        }
        history.addFirst(previousIndex)
        historyPosition = 0
        currentIndex = previousIndex
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return values[currentIndex]
    }
}

