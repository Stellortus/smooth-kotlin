package top.stellortus.smooth_kotlin

import top.stellortus.smooth_kotlin.annotations.Api

class ConditionalTake<T>(val origin: T, val predicate: Boolean)

@Api
inline fun <T> T.ifMeets(predicate: (T) -> Boolean) = ConditionalTake(this, predicate(this))

@Api
inline fun <T> ConditionalTake<T>.thenTake(value: () -> T): T = if (predicate) value() else origin


@Api
inline fun <T> ConditionalTake<T>.elseTake(value: () -> T): T = if (predicate) origin else value()
