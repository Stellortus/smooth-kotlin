package top.stellortus.smooth_kotlin.selectors

import top.stellortus.smooth_kotlin.annotations.Api
import kotlin.reflect.KProperty

@Api
class ShuffledSelector<T>(values: List<T>) : Selector<T>(values) {

    val order by lazy { values.indices.shuffled() }
    override fun getValue(thisRef: Any?, property: KProperty<*>) = values[order[index]]
}