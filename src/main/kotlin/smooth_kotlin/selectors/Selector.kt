package top.stellortus.smooth_kotlin.selectors

import top.stellortus.smooth_kotlin.utils.CyclicIndex
import kotlin.reflect.KProperty

abstract class Selector<T>(val values: List<T>) {

    init {
        require(values.isNotEmpty()) { "Candidates must not be empty" }
    }

    protected var index by CyclicIndex(values.size - 1)

    open fun stepForward() { index++ }

    open fun stepBackward() { index--}

    abstract operator fun getValue(thisRef: Any?, property: KProperty<*>): T
}