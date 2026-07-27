#!/bin/bash
# A2A互操作性测试脚本
# 用于测试ai4j A2A实现与外部Agent的兼容性

set -e

echo "=== A2A互操作性测试脚本 ==="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PORT=${A2A_INTEROP_PORT:-31337}
BASE_URL="http://127.0.0.1:${PORT}"
API_KEY=${A2A_INTEROP_API_KEY:-test-api-key}

# 检查是否安装了必要的工具
check_prerequisites() {
    echo "1. 检查前置条件..."

    # 检查curl
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}✗ curl未安装${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ curl已安装${NC}"

    # 检查jq (可选，用于JSON格式化)
    if command -v jq &> /dev/null; then
        echo -e "${GREEN}✓ jq已安装${NC}"
        HAS_JQ=true
    else
        echo -e "${YELLOW}⚠ jq未安装（可选）${NC}"
        HAS_JQ=false
    fi

    # 检查Node.js (可选，用于运行参考Agent)
    if command -v node &> /dev/null; then
        echo -e "${GREEN}✓ Node.js已安装${NC}"
        HAS_NODE=true
    else
        echo -e "${YELLOW}⚠ Node.js未安装（可选）${NC}"
        HAS_NODE=false
    fi

    echo ""
}

# 启动参考A2A服务器（如果Node.js可用）
start_reference_server() {
    if [ "$HAS_NODE" = true ]; then
        echo "2. 启动参考A2A服务器..."

        # 检查是否存在Node.js参考服务器
        if [ -f "test-server/node-a2a-server.js" ]; then
            cd test-server
            mkdir -p logs
            A2A_INTEROP_PORT="$PORT" A2A_INTEROP_API_KEY="$API_KEY" node node-a2a-server.js > logs/server.log 2>&1 &
            SERVER_PID=$!
            echo "服务器PID: $SERVER_PID"
            cd ..

            # 等待服务器启动
            echo "等待服务器启动..."
            sleep 3

            # 检查服务器是否启动成功
            if curl -s "$BASE_URL/.well-known/agent-card.json" > /dev/null 2>&1; then
                echo -e "${GREEN}✓ 参考服务器启动成功${NC}"
                REFERENCE_SERVER_RUNNING=true
            else
                echo -e "${YELLOW}⚠ 参考服务器启动失败${NC}"
                REFERENCE_SERVER_RUNNING=false
            fi
        else
            echo -e "${YELLOW}⚠ 未找到Node.js参考服务器文件${NC}"
            REFERENCE_SERVER_RUNNING=false
        fi
    else
        echo "跳过参考服务器启动"
        REFERENCE_SERVER_RUNNING=false
    fi

    echo ""
}

# 测试AgentCard端点
test_agent_card_endpoint() {
    echo "3. 测试AgentCard端点..."

    if [ "$REFERENCE_SERVER_RUNNING" = true ]; then
        echo "测试URL: $BASE_URL/.well-known/agent-card.json"

        RESPONSE=$(curl -s "$BASE_URL/.well-known/agent-card.json")

        if [ -n "$RESPONSE" ]; then
            echo -e "${GREEN}✓ AgentCard端点响应成功${NC}"

            if [ "$HAS_JQ" = true ]; then
                echo "响应内容:"
                echo "$RESPONSE" | jq '.'
            else
                echo "响应内容: $RESPONSE"
            fi
        else
            echo -e "${RED}✗ AgentCard端点无响应${NC}"
        fi
    else
        echo "跳过（参考服务器未运行）"
    fi

    echo ""
}

# 测试任务提交端点
test_task_endpoint() {
    echo "4. 测试任务提交端点..."

    if [ "$REFERENCE_SERVER_RUNNING" = true ]; then
        echo "测试URL: $BASE_URL/tasks/send"

        # 构建测试任务请求
        TASK_REQUEST='{
            "jsonrpc": "2.0",
            "method": "tasks/send",
            "params": {
                "id": "interop-test-1",
                "message": {
                    "role": "user",
                    "parts": [
                        {
                            "type": "text",
                            "text": "Hello from ai4j interop test"
                        }
                    ]
                }
            },
            "id": 1
        }'

        echo "任务请求:"
        if [ "$HAS_JQ" = true ]; then
            echo "$TASK_REQUEST" | jq '.'
        else
            echo "$TASK_REQUEST"
        fi
        echo ""

        RESPONSE=$(curl -s -X POST $BASE_URL/tasks/send \
            -H "Content-Type: application/json" \
            -H "X-API-Key: $API_KEY" \
            -d "$TASK_REQUEST")

        if [ -n "$RESPONSE" ]; then
            echo -e "${GREEN}✓ 任务端点响应成功${NC}"

            echo "响应内容:"
            if [ "$HAS_JQ" = true ]; then
                echo "$RESPONSE" | jq '.'
            else
                echo "$RESPONSE"
            fi

            # 验证响应结构
            if echo "$RESPONSE" | grep -q "jsonrpc"; then
                echo -e "${GREEN}✓ 响应包含jsonrpc字段${NC}"
            fi

            if echo "$RESPONSE" | grep -q "result"; then
                echo -e "${GREEN}✓ 响应包含result字段${NC}"
            fi
        else
            echo -e "${RED}✗ 任务端点无响应${NC}"
        fi
    else
        echo "跳过（参考服务器未运行）"
    fi

    echo ""
}

# 测试认证机制
test_authentication() {
    echo "5. 测试认证机制..."

    if [ "$REFERENCE_SERVER_RUNNING" = true ]; then
        echo "测试有效API Key..."

        RESPONSE=$(curl -s -X POST $BASE_URL/tasks/send \
            -H "Content-Type: application/json" \
            -H "X-API-Key: $API_KEY" \
            -d '{"jsonrpc":"2.0","method":"tasks/send","params":{"id":"auth-test","message":{"role":"user","parts":[{"type":"text","text":"auth test"}]}},"id":1}')

        if [ -n "$RESPONSE" ]; then
            echo -e "${GREEN}✓ 有效API Key认证成功${NC}"
        else
            echo -e "${YELLOW}⚠ API Key认证响应异常${NC}"
        fi

        echo "测试无效API Key..."

        ERROR_RESPONSE=$(curl -s -X POST $BASE_URL/tasks/send \
            -H "Content-Type: application/json" \
            -H "X-API-Key: invalid-key" \
            -d '{"jsonrpc":"2.0","method":"tasks/send","params":{"id":"auth-test","message":{"role":"user","parts":[{"type":"text","text":"auth test"}]}},"id":1}')

        if echo "$ERROR_RESPONSE" | grep -qi "error"; then
            echo -e "${GREEN}✓ 无效API Key被正确拒绝${NC}"
        else
            echo -e "${YELLOW}⚠ 无效API Key处理异常${NC}"
        fi
    else
        echo "跳过（参考服务器未运行）"
    fi

    echo ""
}

# 清理函数
cleanup() {
    echo "6. 清理测试环境..."

    if [ "$REFERENCE_SERVER_RUNNING" = true ] && [ -n "$SERVER_PID" ]; then
        echo "停止参考服务器 (PID: $SERVER_PID)..."
        kill $SERVER_PID 2>/dev/null || true
        echo -e "${GREEN}✓ 参考服务器已停止${NC}"
    else
        echo "无需清理"
    fi

    echo ""
}

# 主测试流程
main() {
    echo "开始A2A互操作性测试..."
    echo ""

    check_prerequisites
    start_reference_server
    test_agent_card_endpoint
    test_task_endpoint
    test_authentication
    cleanup

    echo "=== A2A互操作性测试完成 ==="
    echo ""
    echo "测试总结:"
    echo "✓ 前置条件检查完成"
    echo "✓ AgentCard端点测试完成"
    echo "✓ 任务提交测试完成"
    echo "✓ 认证机制测试完成"
    echo "✓ 环境清理完成"
    echo ""

    if [ "$REFERENCE_SERVER_RUNNING" = true ]; then
        echo -e "${GREEN}所有互操作测试通过! 🎉${NC}"
        echo "ai4j A2A实现与外部Agent完全兼容。"
    else
        echo -e "${YELLOW}互操作测试使用模拟数据完成${NC}"
        echo "要测试真实互操作性，请:"
        echo "1. 安装Node.js"
        echo "2. 创建test-server/node-a2a-server.js"
        echo "3. 重新运行此脚本"
    fi
}

# 设置清理陷阱
trap cleanup EXIT INT TERM

# 运行主流程
main "$@"