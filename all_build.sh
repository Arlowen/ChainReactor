#!/bin/bash
# 用法: ./all_build.sh

set -e

echo "🔧 开始构建 ChainReactor 插件..."

# 设置 JAVA_HOME
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home

# 清理旧的构建产物
echo "🧹 清理旧的构建产物..."
./gradlew clean

# 构建插件
echo "📦 构建插件..."
./gradlew buildPlugin

# 显示构建结果
echo ""
echo "✅ 构建完成！"
echo ""
echo "📁 插件包位置:"
ls -la build/distributions/*.zip
echo ""
echo "📌 安装方法:"
echo "   1. 打开 IntelliJ IDEA"
echo "   2. Settings → Plugins → ⚙️ → Install Plugin from Disk..."
echo "   3. 选择上面的 .zip 文件"
echo "   4. 重启 IDE"
