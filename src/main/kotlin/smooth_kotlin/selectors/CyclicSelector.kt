package top.stellortus.smooth_kotlin.selectors

import top.stellortus.smooth_kotlin.annotations.Api
import kotlin.reflect.KProperty

@Api
class CyclicSelector<T>(values: List<T>) : Selector<T>(values) {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = values[index]
}