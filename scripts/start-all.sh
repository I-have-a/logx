#!/bin/bash

echo "🚀 开始构建 LogX 项目..."

# 进入项目根目录
cd "$(dirname "$0")/.."

# 清理并编译
echo "📦 清理旧文件..."
mvn clean

echo "🔨 编译所有模块..."
mvn install -DskipTests

echo "✅ 构建完成！"

# 列出所有生成的 JAR
echo "📋 生成的 JAR 文件："
find . -name "*.jar" -not -path "*/target/lib/*" -not -name "*-sources.jar" -not -name "*-javadoc.jar"