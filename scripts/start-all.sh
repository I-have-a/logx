#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== LogX 一键部署脚本 ===${NC}"

# 1. 检查环境
echo -e "${YELLOW}[1/6] 检查环境...${NC}"

# 检查Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker未安装${NC}"
    exit 1
fi

# 检查Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose未安装${NC}"
    exit 1
fi

# 检查JDK
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ JDK未安装${NC}"
    exit 1
else
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ]; then
        echo -e "${RED}❌ JDK版本过低,需要JDK 17+${NC}"
        exit 1
    fi
fi

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven未安装${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 环境检查通过${NC}"

# 2. 启动中间件
echo -e "${YELLOW}[2/6] 启动中间件 (MySQL, Redis, ES, Kafka, MinIO)...${NC}"
docker-compose up -d

echo "等待中间件就绪..."
sleep 30

# 检查中间件状态
if ! docker ps | grep -q logx-mysql; then
    echo -e "${RED}❌ MySQL启动失败${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 中间件启动成功${NC}"

# 3. 初始化数据库
echo -e "${YELLOW}[3/6] 初始化数据库...${NC}"
docker exec -i logx-mysql mysql -uroot -proot123 < scripts/init.sql
echo -e "${GREEN}✅ 数据库初始化完成${NC}"

# 4. 编译项目
echo -e "${YELLOW}[4/6] 编译项目...${NC}"
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 编译失败${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 编译完成${NC}"

# 5. 创建日志目录
echo -e "${YELLOW}[5/6] 创建日志目录...${NC}"
mkdir -p logs

# 6. 启动服务
echo -e "${YELLOW}[6/6] 启动LogX服务...${NC}"

# 选择部署模式
read -p "选择部署模式 [1=单体, 2=微服务]: " mode

if [ "$mode" == "1" ]; then
    # 单体模式
    echo "启动单体应用..."
    nohup java -jar logx-standalone/target/logx-standalone-0.0.1-SNAPSHOT.jar \
        > logs/standalone.log 2>&1 &
    echo $! > logs/standalone.pid
    echo -e "${GREEN}✅ 单体应用启动中...${NC}"
    
else
    # 微服务模式
    echo "启动微服务..."
    
    # HTTP网关
    nohup java -jar logx-gateway/logx-gateway-http/target/logx-gateway-http-0.0.1-SNAPSHOT.jar \
        > logs/gateway-http.log 2>&1 &
    echo $! > logs/gateway-http.pid
    
    # 日志处理器
    nohup java -jar logx-engine/logx-engine-processor/target/logx-engine-processor-0.0.1-SNAPSHOT.jar \
        > logs/processor.log 2>&1 &
    echo $! > logs/processor.pid
    
    # 异常检测
    nohup java -jar logx-engine/logx-engine-detection/target/logx-engine-detection-0.0.1-SNAPSHOT.jar \
        > logs/detection.log 2>&1 &
    echo $! > logs/detection.pid
    
    # 存储管理
    nohup java -jar logx-engine/logx-engine-storage/target/logx-engine-storage-0.0.1-SNAPSHOT.jar \
        > logs/storage.log 2>&1 &
    echo $! > logs/storage.pid
    
    # 管理控制台
    nohup java -jar logx-console/logx-console-api/target/logx-console-api-0.0.1-SNAPSHOT.jar \
        > logs/console-api.log 2>&1 &
    echo $! > logs/console-api.pid
    
    echo -e "${GREEN}✅ 微服务启动中...${NC}"
fi

echo ""
echo -e "${GREEN}=== 部署完成! ===${NC}"
echo ""
echo "服务地址:"
echo "  - HTTP网关:    http://localhost:10240"
echo "  - 管理控制台:   http://localhost:8083"
echo "  - API文档:     http://localhost:8083/doc.html"
echo "  - Kibana:      http://localhost:5601"
echo "  - MinIO:       http://localhost:9001"
echo ""
echo "查看日志:"
echo "  tail -f logs/*.log"
echo ""