#!/bin/bash

echo "Setting up SkyWalking Agent for Adam Monitor SDK testing..."

# 创建skywalking-agent目录
mkdir -p skywalking-agent

# 下载SkyWalking Agent
SKYWALKING_VERSION="9.5.0"
AGENT_URL="https://archive.apache.org/dist/skywalking/${SKYWALKING_VERSION}/apache-skywalking-apm-${SKYWALKING_VERSION}.tar.gz"

if [ ! -f "skywalking-agent/skywalking-agent.jar" ]; then
    echo "Downloading SkyWalking Agent..."
    curl -L -o skywalking-agent.tar.gz "${AGENT_URL}"
    
    echo "Extracting SkyWalking Agent..."
    tar -xzf skywalking-agent.tar.gz
    
    # 移动agent文件到指定目录
    mv apache-skywalking-apm-bin/agent/* skywalking-agent/
    
    # 清理临时文件
    rm -rf apache-skywalking-apm-bin skywalking-agent.tar.gz
    
    echo "SkyWalking Agent setup completed!"
else
    echo "SkyWalking Agent already exists, skipping download."
fi

# 确保脚本可执行
chmod +x skywalking-agent/skywalking-agent.jar 2>/dev/null || true

echo "Setup completed! You can now run: docker-compose -f docker-compose-skywalking.yaml up" 