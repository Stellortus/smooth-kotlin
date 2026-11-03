package top.stellortus.smooth_kotlin

import top.stellortus.smooth_kotlin.annotations.Api

@Api fun String.toIntOr(default: Int) = this.toIntOrNull() ?: default

@Api fun String.toLongOr(default: Long) = this.toLongOrNull() ?: default