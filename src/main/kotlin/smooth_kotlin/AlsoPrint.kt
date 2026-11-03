package top.stellortus.smooth_kotlin

import top.stellortus.smooth_kotlin.annotations.Api

@Api
fun <T> T.alsoPrint(): T {
    print(this)
    return this
}

@Api
fun <T> T.alsoPrintln(): T {
    println(this)
    return this
}