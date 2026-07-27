#!/usr/bin/env node
/**
 * Node.js参考A2A服务器 - 用于互操作性测试
 *
 * 这是一个简单的Node.js实现的A2A服务器，符合A2A 1.0规范。
 * 用于测试ai4j A2A实现的互操作性。
 *
 * 运行: node node-a2a-server.js
 * 默认端口: 3000
 */

const http = require('http');
const url = require('url');

// 配置
const PORT = Number(process.env.PORT || process.env.A2A_INTEROP_PORT || 31337);
const API_KEY = process.env.A2A_INTEROP_API_KEY || 'test-api-key';

// AgentCard定义
const AGENT_CARD = {
    name: 'nodejs-a2a-agent',
    description: 'Node.js A2A Agent for interoperability testing',
    version: '1.0.0',
    url: `http://localhost:${PORT}`,
    protocol: 'a2a/1.0',
    agentUri: 'agent://nodejs-a2a',
    capabilities: ['chat', 'analysis', 'code-review'],
    skills: [
        {
            name: 'conversation',
            description: 'Natural language conversation capability'
        },
        {
            name: 'json-response',
            description: 'Respond in JSON format'
        }
    ],
    authentication: {
        type: 'api-key',
        header: 'X-API-Key',
        obtainAt: `http://localhost:${PORT}/register`
    },
    endpoints: {
        chat: 'POST /tasks/send',
        analysis: 'POST /tasks/send'
    }
};

// 创建HTTP服务器
const server = http.createServer((req, res) => {
    // 设置CORS头
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-API-Key');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    const parsedUrl = url.parse(req.url, true);
    const pathname = parsedUrl.pathname;

    console.log(`${new Date().toISOString()} - ${req.method} ${pathname}`);

    if (req.method === 'GET' && (pathname === '/.well-known/agent-card.json' || pathname === '/.well-known/agent.json')) {
        handleAgentCardRequest(req, res);
    } else if (req.method === 'POST' && pathname === '/tasks/send') {
        handleTaskRequest(req, res);
    } else {
        sendNotFound(res);
    }
});

// 处理AgentCard请求
function handleAgentCardRequest(req, res) {
    res.setHeader('Content-Type', 'application/json');
    res.writeHead(200);
    res.end(JSON.stringify(AGENT_CARD, null, 2));
    console.log('  → AgentCard sent');
}

// 处理任务请求
function handleTaskRequest(req, res) {
    // 检查API Key
    const providedKey = req.headers['x-api-key'];
    if (providedKey !== API_KEY) {
        sendAuthError(res);
        return;
    }

    let body = '';

    req.on('data', (chunk) => {
        body += chunk.toString();
    });

    req.on('end', () => {
        try {
            const request = JSON.parse(body);

            console.log('  → Task request received');
            console.log(`    ID: ${request.params?.id || 'unknown'}`);
            console.log(`    Message: ${request.params?.message?.parts?.[0]?.text || 'empty'}`);

            // 处理任务
            const taskId = request.params?.id || 'task-' + Date.now();
            const userMessage = request.params?.message?.parts?.[0]?.text || '';

            // 生成响应
            const responseText = processTask(userMessage);
            const response = buildA2AResponse(taskId, responseText);

            res.setHeader('Content-Type', 'application/json');
            res.writeHead(200);
            res.end(JSON.stringify(response, null, 2));

            console.log('  → Task response sent');

        } catch (error) {
            console.error('  → Error processing request:', error.message);
            sendBadRequest(res, error.message);
        }
    });
}

// 处理任务并生成响应
function processTask(message) {
    if (!message || message.trim().length === 0) {
        return 'Please provide a message';
    }

    // 简单的任务处理逻辑
    const lowerMessage = message.toLowerCase();

    if (lowerMessage.includes('hello') || lowerMessage.includes('hi')) {
        return 'Hello! I am the Node.js A2A test agent. How can I help you?';
    } else if (lowerMessage.includes('json')) {
        return JSON.stringify({
            message: 'Here is your JSON response',
            timestamp: new Date().toISOString(),
            agent: 'nodejs-a2a-agent'
        }, null, 2);
    } else if (lowerMessage.includes('error')) {
        // 模拟错误场景
        throw new Error('Simulated error for testing');
    } else {
        return `I received your message: "${message}". This is a test response from the Node.js A2A agent.`;
    }
}

// 构建A2A响应
function buildA2AResponse(taskId, text) {
    return {
        jsonrpc: '2.0',
        result: {
            id: taskId,
            status: {
                state: 'completed',
                message: 'Task completed successfully'
            },
            artifacts: [
                {
                    parts: [
                        {
                            type: 'text',
                            text: text
                        }
                    ]
                }
            ]
        },
        id: 1
    };
}

// 发送认证错误
function sendAuthError(res) {
    const error = {
        jsonrpc: '2.0',
        error: {
            code: 'AUTHENTICATION_FAILED',
            message: 'Invalid or missing API key'
        },
        id: null
    };

    res.setHeader('Content-Type', 'application/json');
    res.writeHead(401);
    res.end(JSON.stringify(error, null, 2));
    console.log('  → Auth error sent');
}

// 发送400错误
function sendBadRequest(res, message) {
    const error = {
        jsonrpc: '2.0',
        error: {
            code: 'INVALID_REQUEST',
            message: message || 'Invalid request format'
        },
        id: null
    };

    res.setHeader('Content-Type', 'application/json');
    res.writeHead(400);
    res.end(JSON.stringify(error, null, 2));
    console.log('  → Bad request error sent');
}

// 发送404错误
function sendNotFound(res) {
    const error = {
        jsonrpc: '2.0',
        error: {
            code: 'TASK_NOT_FOUND',
            message: 'Endpoint not found'
        },
        id: null
    };

    res.setHeader('Content-Type', 'application/json');
    res.writeHead(404);
    res.end(JSON.stringify(error, null, 2));
    console.log('  → Not found error sent');
}

// 启动服务器
server.listen(PORT, '127.0.0.1', () => {
    console.log('=== Node.js A2A Reference Server ===');
    console.log(`Server running at http://localhost:${PORT}/`);
    console.log('');
    console.log('Endpoints:');
    console.log(`  GET  http://localhost:${PORT}/.well-known/agent.json`);
    console.log(`  POST http://localhost:${PORT}/tasks/send`);
    console.log('');
    console.log('Authentication:');
    console.log(`  Header: X-API-Key: ${API_KEY}`);
    console.log('');
    console.log('Press Ctrl+C to stop');
    console.log('=====================================');
});

// 优雅关闭
process.on('SIGINT', () => {
    console.log('\nShutting down server...');
    server.close(() => {
        console.log('Server closed');
        process.exit(0);
    });
});