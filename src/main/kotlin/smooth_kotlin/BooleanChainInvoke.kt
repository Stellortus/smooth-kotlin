package top.stellortus.smooth_kotlin

import top.stellortus.smooth_kotlin.annotations.Api

@Api
inline fun Boolean.ifTrue(block: () -> Unit): Boolean {
    if (this) block()
    return this
}

@Api
inline fun Boolean.ifFalse(block: () -> Unit): Boolean {
    if (!this) block()
    return this
}
