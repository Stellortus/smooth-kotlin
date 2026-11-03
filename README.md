# smooth-kotlin

一个 Kotlin/JVM 工具库，提供布尔链式调用、条件取值、字符串数字转换、打印辅助、选择器和循环下标委托。

以下示例省略导入语句，可通过 IDE 自动导入对应声明。

## 安装

当前项目使用 Kotlin 2.4.20 构建，JVM 工具链为 Java 17；使用方需运行在 Java 17 或更高版本，并使用能读取该库元数据的 Kotlin 编译器。

在 Gradle 的依赖仓库配置中添加 JitPack。例如，在 `settings.gradle.kts` 中配置：

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

在模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.github.Stellortus:smooth-kotlin:<version>")
}
```

将 `<version>` 替换为 [GitHub Releases](https://github.com/Stellortus/smooth-kotlin/releases) 中已发布、且 JitPack 构建成功的标签，保留标签本身的写法。本说明不保证某个具体版本已可下载，也不代表该库已发布到 Maven Central。

## 布尔链式调用

`ifTrue` 在接收值为 `true` 时执行代码块，`ifFalse` 在接收值为 `false` 时执行。两者均返回原布尔值，因此可以继续链式调用。

```kotlin
val enabled = true
val result = enabled
    .ifTrue { println("已启用") }
    .ifFalse { println("未启用") }

println(result)
// 输出：
// 已启用
// true
```

代码块不负责改变返回的布尔值；未选中的代码块不会执行。

## 条件取值

`ifMatches` 立即判断原值是否符合条件，返回保存原值和判断结果的 `ConditionalTake<T>`。随后可选择：

| 调用 | 条件为真 | 条件为假 |
| --- | --- | --- |
| `thenTake { value }` | 计算并返回 `value` | 返回原值 |
| `elseTake { value }` | 返回原值 | 计算并返回 `value` |

```kotlin
val replaced = 5.ifMatches { it > 0 }.thenTake { 10 }
println(replaced) // 10

val fallback = (-1).ifMatches { it > 0 }.elseTake { 0 }
println(fallback) // 0

val unchanged = 5.ifMatches { it > 0 }.elseTake { 0 }
println(unchanged) // 5
```

替代值通过 lambda 按需计算，只有选择对应分支时才执行：

```kotlin
var evaluations = 0
val choice = 5.ifMatches { it > 0 }

val first = choice.thenTake { evaluations++; 10 }
val second = choice.thenTake { evaluations++; 20 }
val skipped = (-1).ifMatches { it > 0 }.thenTake {
    error("这个分支不会执行")
}

println(listOf(first, second, skipped, evaluations)) // [10, 20, -1, 2]
```

条件在 `ifMatches` 时只计算一次；替代值不缓存，每次选中分支都会重新执行 lambda。`thenTake` 和 `elseTake` 返回的是 `T`，不再是 `ConditionalTake<T>`，因此它们不是一组连续的 `if/else` 链。

## 字符串数字转换

`toIntOr` 和 `toLongOr` 在转换失败（包括超出目标类型范围）时返回指定默认值。

```kotlin
println("42".toIntOr(0))              // 42
println("invalid".toIntOr(-1))        // -1
println("2147483648".toIntOr(0))      // 0：超出 Int 范围
println("12345678900".toLongOr(0L))   // 12345678900
println("".toLongOr(-1L))             // -1
```

解析规则与标准库 `toIntOrNull()`、`toLongOrNull()` 一致，不会自动去除字符串两端的空白。默认值是普通参数，会在调用前求值。

## 打印辅助

`alsoPrint()` 输出接收值，`alsoPrintln()` 输出后换行。两者均返回原值 `this`，保留原有的泛型类型，可以继续链式调用。

```kotlin
"Hello, ".alsoPrint()
"Kotlin!".alsoPrintln()
// 输出一行：Hello, Kotlin!
```

打印后可以继续访问原值的属性或调用其他函数：

```kotlin
val length = "Kotlin".alsoPrintln().length // 输出 Kotlin，length 为 6
```

也支持可空接收值：

```kotlin
val value: String? = null
val result: String? = value.alsoPrintln() // 输出 null，返回 null
```

## 选择器

三个选择器都接收非空 `List<T>`，继承 `Selector<T>`，通过只读属性委托访问当前元素：

```kotlin
val selector = CyclicSelector(listOf("A", "B", "C"))
val current by selector

println(current) // A：读取不会移动
selector.stepForward()
println(current) // B：显式前进后再读取
```

`stepForward()`、`stepBackward()` 只改变选择位置，返回 `Unit`。委托属性会在每次读取时获取当前值，不是首次读取的快照；不要用移动方法的返回值获取元素。

| 类型 | 初始读取 | `stepForward()` | `stepBackward()` |
| --- | --- | --- | --- |
| `CyclicSelector` | 列表首项 | 前进一位，到末尾后回到首项 | 后退一位，到首项前回到末尾 |
| `RandomSelector` | 构造时随机抽取的值 | 重放右侧历史，或在末尾生成新记录 | 重放左侧历史，或在头部生成新记录 |
| `ShuffledSelector` | 洗牌顺序的首项 | 按洗牌顺序前进并循环 | 按洗牌顺序后退并循环 |

空列表会抛出 `IllegalArgumentException`。读取类型为 `T`，候选类型可空时才可能读取到 `null`。候选列表应在使用期间保持不变；不要从外部修改选择器的历史队列或推进其随机源，以免破坏导航状态。

### 循环选择

初始下标为 `0`，首次读取返回首项。首次前进后指向第二项，首次后退后指向末项；只有一个候选值时，向任意方向移动仍指向它。

```kotlin
val selector = CyclicSelector(listOf("A", "B", "C"))
val current by selector

println(current) // A
selector.stepBackward()
println(current) // C
selector.stepForward()
println(current) // A
selector.stepForward()
println(current) // B
```

### 随机选择

```kotlin
val selector = RandomSelector(listOf("A", "B", "C"), memoryLimit = 3)
val current by selector

val initial = current
selector.stepForward()
val next = current
selector.stepBackward()
println(current == initial) // true：回到已有记录
selector.stepForward()
println(current == next)    // true：重放记录，不重新抽取
```

读取不会重新抽取。只有移动到历史两端之外才会生成新记录；向头部生成时，后续前进同样可以重放这些记录。允许连续抽到同一个值，每次新抽取仍占一条记录。

`memoryLimit` 控制保存的记录总数，包含当前记录：

| 值 | 行为 |
| --- | --- |
| `-1`（默认） | 无限保存；随着新的抽取增长，内存占用也增长 |
| `0` | 不保存历史，两种方向的移动都重新随机，重复读取仍保持当前值 |
| 正数 | 最多保存指定条数；尾部追加时淘汰头部，头部插入时淘汰尾部 |
| 小于 `-1` | 抛出 `IllegalArgumentException` |

例如 `memoryLimit = 1` 只保存当前记录，每次移动都会重新抽取；`memoryLimit = 3` 最多保存三条，而不是三条加当前记录。已被淘汰的记录无法恢复，再次越过历史边界会生成新记录。

当前随机源以候选列表的 `hashCode()` 为种子，不适合需要不可预测随机性的用途。

### 洗牌后循环

```kotlin
val selector = ShuffledSelector(listOf("A", "B", "C"))
val current by selector
val round = List(3) {
    val selected = current
    selector.stepForward()
    selected
}
println(round.toSet() == setOf("A", "B", "C")) // true
println(current == round.first())              // true：一轮后回到起点
```

顺序在首次访问 `order`（通常是首次读取委托属性）时生成，后续循环不会重新洗牌。读取不会移动，前进和后退互为反向操作；候选值不重复时，一整轮会遍历所有候选值。

## 循环下标委托

`CyclicIndex` 用于委托一个 `Int` 变量。赋值超出范围时，按范围长度循环回绕，支持递增、递减和跨越多个位置。

```kotlin
var index by CyclicIndex(5..7)
println(index) // 5
index--
println(index) // 7
index++
println(index) // 5
index += 5
println(index) // 7
index = 4
println(index) // 7
```

可使用以下构造方式，范围两端均包含：

| 构造方式 | 实际范围 | 初始值 |
| --- | --- | --- |
| `CyclicIndex(2)` | `0..2` | `0` |
| `CyclicIndex(5, 7)` | `5..7` | `5` |
| `CyclicIndex(-5..-3)` | `-5..-3` | `-5` |
| `CyclicIndex(7 downTo 5)` | `5..7` | `5` |
| `CyclicIndex(1..9 step 2)` | `1..9`，步长不保留 | `1` |

使用有效、非空的范围；单参数构造的 `end` 应不小于 `0`，双参数构造应满足 `start <= end`。倒序序列会转为升序连续范围，不保留遍历方向。当前计算使用 `Int`，不保证超大范围或整数溢出情况下的回绕行为。

## `@Api` 与 IDEA 未使用检查

`@Api` 用于标记对外提供的声明。本项目通过 `.idea/misc.xml` 将其完整名称注册为 IDEA 入口点注解，使已标注的 API 不被提示为未使用。

```kotlin
@Api
fun libraryEntry(): String = "供其他项目调用"
```

如果在其他项目中使用该注解，需要在 IDEA 设置中搜索 `Unused symbol`，找到 Kotlin 对应检查，在入口点注解配置中添加：

```text
top.stellortus.smooth_kotlin.annotations.Api
```

仅添加库依赖不会自动导入本项目的 `.idea` 配置。注解采用 `SOURCE` 保留策略，不提供运行时逻辑，也不会改变函数执行结果。
