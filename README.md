# ChainReactor

![Build](https://github.com/Arlowen/ChainReactor/workflows/Build/badge.svg)

ChainReactor 是一个 IntelliJ IDEA 工具窗插件，用于将多个子项目的构建脚本串联成“流水线”，按顺序执行并输出统一日志。

<!-- Plugin description -->
ChainReactor 是一款用于多模块构建串行执行的 IntelliJ IDEA 插件。它会扫描项目根目录下的子模块，并以“流水线”的方式按顺序执行脚本，提供可视化状态、统一日志和可复用的 Profile。

主要特性：
- 自动扫描 Maven/Gradle 子模块
- 拖拽排序、启用/禁用模块、单模块自定义命令
- 串行执行流水线，实时日志与执行状态
- Profile 保存/编辑/运行/停止
- 执行超时与失败继续策略
<!-- Plugin description end -->

## 功能概览
- **模块扫描**：扫描项目根目录下包含 `pom.xml` / `build.gradle` / `build.gradle.kts` / `settings.gradle` / `settings.gradle.kts` 的子目录。
- **构建流水线**：按列表顺序串行执行，支持停止与失败处理策略。
- **Profile**：保存当前流水线配置（顺序、禁用项、自定义命令），支持编辑与一键运行。
- **日志与状态**：每个模块有状态图标，控制台实时输出。
- **手动添加项目**：可添加不在根目录下的外部项目目录。

## 兼容性
- IntelliJ IDEA **2024.2+**（since build: `242`）

## 安装
### 本地安装（推荐用于当前仓库）
1. 构建插件：
   ```bash
   ./gradlew buildPlugin
   ```
2. 在 IDE 中安装：
   `Settings/Preferences → Plugins → ⚙️ → Install Plugin from Disk...`
3. 选择生成的 zip：
   `build/distributions/*.zip`

### Marketplace
当前仓库未配置 Marketplace ID，若后续发布，可补充此部分。

## 使用说明
### 打开工具窗
- `View → Tool Windows → ChainReactor`（工具窗位于右侧）

### 扫描模块
- 点击工具栏的 **刷新** 按钮扫描项目。
- 若未识别到模块，确认子目录中存在 Maven/Gradle 构建文件。

### 模块列表操作
- **拖拽**调整执行顺序。
- **单击左侧复选框**启用/禁用模块。
- **双击模块**编辑自定义命令。
- **移除项目**可从列表中移除模块（不会删除物理文件）。

### 运行流水线
- 点击 **运行** 按钮开始执行。
- 点击 **停止** 会终止当前脚本并跳过后续模块。

### Profile 管理
- 点击 **保存** 将当前列表保存为 Profile。
- Profile 列表支持：运行/停止/编辑/删除。
- Profile 运行会创建独立日志 Tab，不影响当前列表。

### 运行单个脚本
- `Tools → 运行脚本...` 选择任意 Shell 脚本执行，输出在独立控制台窗口。

## 脚本约定
- 默认执行命令为模块目录中的 `./all_build.sh`。
- 若脚本不存在，可为模块设置**自定义命令**替代。
- 脚本通过 `/bin/bash -c` 执行，请确保脚本可执行权限。

## 配置
`Settings/Preferences → Tools → ChainReactor`
- **执行超时（秒）**：单个脚本最大执行时间，默认 300 秒。
- **失败时继续执行**：开启后，某模块失败仍继续后续模块。
- **脚本文件名**：当前版本仍以 `./all_build.sh` 作为默认执行入口，建议通过自定义命令精确控制。

## 开发与调试
```bash
# 启动 IDE（运行插件）
./gradlew runIde

# 运行测试
./gradlew test

# 构建插件包
./gradlew buildPlugin
```

## 常见问题
- **未发现模块**：确认子目录包含 Maven/Gradle 构建文件；或使用“添加项目”手动添加路径。
- **脚本执行失败**：检查脚本是否存在、是否有可执行权限，以及工作目录是否正确。
- **停止无效**：插件会销毁当前进程，长时间运行的子进程可能需要脚本自行处理退出。
