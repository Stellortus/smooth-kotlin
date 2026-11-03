package top.stellortus.smooth_kotlin.utils

import top.stellortus.smooth_kotlin.annotations.Api
import kotlin.reflect.KProperty

@Api
class CyclicIndex(start: Int, end: Int) {

    val progression = (start..end)

    @Api
    constructor(end: Int) : this(0, end)

    @Api
    constructor(progression: IntProgression) : this(
        if (progression.step > 0) progression.first() else progression.last(),
        if (progression.step > 0) progression.last() else progression.first()
    )

    private var index = progression.first

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = this.index

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        index = value
        if (value in progression) return
        val overflow = (value - progression.first).floorDiv(progression.count())
        index -= overflow * progression.count()
    }
}

