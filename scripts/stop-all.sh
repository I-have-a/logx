#!/bin/bash

echo "停止LogX服务..."

# 读取PID并停止
for pidfile in logs/*.pid; do
    if [ -f "$pidfile" ]; then
        pid=$(cat "$pidfile")
        if ps -p $pid > /dev/null; then
            echo "停止进程 $pid..."
            kill $pid
        fi
        rm -f "$pidfile"
    fi
done

echo "停止中间件..."
docker-compose down

echo "✅ 全部停止完成"