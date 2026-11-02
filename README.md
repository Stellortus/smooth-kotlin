# Kotlin 库模板

基于 Kotlin/JVM、Gradle Kotlin DSL 和 JDK 17 的单模块库模板，支持 GitHub Release 和 JitPack 自动发布。

## 创建新项目

1. `git clone <模板仓库地址> <新项目目录>`，进入新项目目录。
2. 修改 `settings.gradle.kts` 中的 `rootProject.name`。
3. 修改 `gradle.properties` 中的 `group` 和 `version`，按需修改 `src` 中的包名与代码。
4. 在 GitHub 创建一个空的**公开仓库**，然后修改远程地址：

   ```shell
   git remote set-url origin <新仓库地址>
   git add .
   git commit -m "Initialize library"
   git push -u origin HEAD
   ```

此方式保留模板的提交历史，但普通 clone 不会把本地标签推送到新仓库。不要使用 `git push --mirror` 或 `git push --tags` 迁移模板标签。

首次推送时，将当前分支设为 GitHub 默认分支，并确保 GitHub Actions 已启用。工作流自动识别默认分支与仓库名，无需填写用户名、仓库名或额外的发布 Secret。若组织策略或标签规则限制 `GITHUB_TOKEN` 创建标签／Release，需要先在仓库设置中允许此操作。

## 版本与发布

项目版本只在 `gradle.properties` 的 `version` 中维护，格式为不带前缀的 `主版本.次版本.补丁版本`，不接受 SNAPSHOT 或预发布后缀。Kotlin 插件、Gradle Wrapper 等工具版本独立维护。

发布步骤：修改 `version`，提交代码，push 到 GitHub 默认分支。

工作流会先运行测试、构建并验证 Maven 发布，再自动创建以该版本号命名的 Git 标签和 GitHub Release，附上主 JAR 和源码 JAR，并生成发布说明。随后请求 JitPack 构建并确认 POM 可以下载。其他分支 push 和 PR 只执行检查。

同一版本的后续 push 不会移动标签或覆盖已有 Release；只有修改版本号才会发布新的代码。若标签已存在但 Release 不存在，仅在标签指向本次提交时补建 Release，否则失败并提示使用新版本。

JitPack 会从标签拉取源码构建，发布坐标为 `com.github.<仓库所有者>:<仓库名>:<标签>`，与源码包名及本地 `group` 无关。`jitpack.yml` 自动使用 JitPack 提供的坐标参数，运行完整构建并发布到其本地 Maven 仓库。

若 JitPack 暂时不可用，工作流最多尝试 8 次，每次请求最多等待 90 秒，间隔 15 秒。失败时 GitHub Release 保留；在 Actions 页面选择 **Re-run failed jobs** 即可重试。构建日志位于 `https://jitpack.io/com/github/<仓库所有者>/<仓库名>/<版本>/build.log`。

若失败原因是源码或构建配置有误，需要修复代码并提升 `gradle.properties` 中的版本，再提交并 push。已有标签仍指向旧提交；重跑旧工作流不会加载新的修复，不要移动已有发布标签。

## 在其他项目中引用

将占位符替换为新仓库的信息，版本号与 Release 标签完全相同。

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.<仓库所有者>:<仓库名>:<版本>")
}
```

若消费项目统一在 `settings.gradle.kts` 管理仓库，应把 JitPack 加到其 `dependencyResolutionManagement.repositories` 中。

## 本地验证

Windows：

```powershell
.\gradlew.bat -q printVersion
.\gradlew.bat build publishToMavenLocal "-Dmaven.repo.local=$PWD/build/verification-repository"
```

Linux / macOS：

```shell
./gradlew -q printVersion
./gradlew build publishToMavenLocal -Dmaven.repo.local="$PWD/build/verification-repository"
```

主 JAR 与源码 JAR 位于 `build/libs`，验证用 Maven 产物位于 `build/verification-repository`。

Git 中的 `gradlew` 应保存为可执行文件（模式 `100755`），`.gitattributes` 固定其使用 LF 换行。CI 和 JitPack 也会在构建前执行 `chmod +x gradlew`。如果 Windows 下重新生成 Wrapper 后丢失 Git 可执行位，提交前执行：

```shell
git update-index --chmod=+x gradlew
```

使用 `git ls-files --stage gradlew` 确认模式为 `100755`，并将权限变更随代码一起提交。仅在本机执行 `chmod` 无法保证 Windows Git 会记录此变更。

发布自动化测试使用 Node.js 22 或更新版本，无需安装 npm 依赖，也不会调用真实的发布接口：

```shell
node --test .github/scripts/release.test.mjs
```

参考：[JitPack 构建配置](https://docs.jitpack.io/building/)、[GitHub CLI 创建 Release](https://cli.github.com/manual/gh_release_create)。
