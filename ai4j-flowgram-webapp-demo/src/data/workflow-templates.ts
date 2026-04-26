import { FlowDocumentJSON } from '../typings';
import { WorkflowNodeType } from '../nodes';
import { FlowValueUtils } from '@flowgram.ai/form-materials';

export interface WorkflowTemplate {
  id: string;
  name: string;
  description: string;
  badge?: string;
  inputs: Record<string, unknown>;
  document: FlowDocumentJSON;
}

const stringSchema = (defaultValue?: string) => ({
  type: 'string',
  ...(defaultValue === undefined ? {} : { default: defaultValue }),
});

const numberSchema = (defaultValue?: number) => ({
  type: 'number',
  ...(defaultValue === undefined ? {} : { default: defaultValue }),
});

const arraySchema = (items: Record<string, unknown>, defaultValue?: unknown[]) => ({
  type: 'array',
  items,
  ...(defaultValue === undefined ? {} : { default: defaultValue }),
});

const objectSchema = (
  properties: Record<string, unknown>,
  required: string[] = []
): Record<string, unknown> => ({
  type: 'object',
  required,
  properties,
});

const constantValue = (content: string | number | boolean | unknown[]) => ({
  type: 'constant' as const,
  content,
  schema: FlowValueUtils.inferConstantJsonSchema({
    type: 'constant' as const,
    content,
  }),
});

const refValue = (...content: string[]) => ({
  type: 'ref' as const,
  content,
});

const templateValue = (content: string) => ({
  type: 'template' as const,
  content,
});

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value));

const blankCanvasTemplate: WorkflowTemplate = {
  id: 'blank-canvas',
  name: '空白画布',
  description: '仅保留 Start 与 End，适合从零开始搭建流程。',
  badge: 'Quick Start',
  inputs: {
    message: 'hello-flowgram',
  },
  document: {
    nodes: [
      {
        id: 'start_0',
        type: WorkflowNodeType.Start,
        meta: {
          position: {
            x: 120,
            y: 260,
          },
        },
        data: {
          title: 'Start',
          outputs: objectSchema(
            {
              message: stringSchema('hello-flowgram'),
            },
            ['message']
          ),
        },
      },
      {
        id: 'end_0',
        type: WorkflowNodeType.End,
        meta: {
          position: {
            x: 760,
            y: 260,
          },
        },
        data: {
          title: 'End',
          inputs: objectSchema(
            {
              result: stringSchema(),
            },
            ['result']
          ),
          inputsValues: {
            result: refValue('start_0', 'message'),
          },
        },
      },
    ],
    edges: [
      {
        sourceNodeID: 'start_0',
        targetNodeID: 'end_0',
      },
    ],
  },
};

const travelCopilotTemplate: WorkflowTemplate = {
  id: 'travel-copilot',
  name: '出行 Copilot',
  description: '串联 Variable、HTTP、Tool、Code、LLM 的完整业务示例。',
  badge: 'Default',
  inputs: {
    departure: '杭州',
    destination: '上海',
    date: '2026-04-02',
    trainType: '高铁',
    userGoal: '我想上午出发，优先舒适和准点，并给我一句简短建议',
  },
  document: {
    nodes: [
      {
        id: 'start_0',
        type: WorkflowNodeType.Start,
        meta: {
          position: {
            x: 120,
            y: 260,
          },
        },
        data: {
          title: 'Start',
          outputs: objectSchema(
            {
              departure: stringSchema('杭州'),
              destination: stringSchema('上海'),
              date: stringSchema('2026-04-02'),
              trainType: stringSchema('高铁'),
              userGoal: stringSchema('我想上午出发，优先舒适和准点，并给我一句简短建议'),
            },
            ['departure', 'destination', 'date', 'trainType', 'userGoal']
          ),
        },
      },
      {
        id: 'variable_0',
        type: WorkflowNodeType.Variable,
        meta: {
          position: {
            x: 420,
            y: 220,
          },
        },
        data: {
          title: '变量整理',
          assign: [
            {
              operator: 'declare',
              left: 'serviceId',
              right: constantValue('minimax-coding'),
            },
            {
              operator: 'declare',
              left: 'modelName',
              right: constantValue('MiniMax-M2.1'),
            },
            {
              operator: 'declare',
              left: 'routeLabel',
              right: templateValue('${start_0.departure} -> ${start_0.destination}'),
            },
            {
              operator: 'declare',
              left: 'travelIntent',
              right: templateValue(
                '${start_0.date} ${start_0.trainType} ${start_0.userGoal}'
              ),
            },
          ],
        },
      },
      {
        id: 'http_0',
        type: WorkflowNodeType.HTTP,
        meta: {
          position: {
            x: 760,
            y: 220,
          },
        },
        data: {
          title: '天气查询',
          api: {
            method: 'GET',
            url: templateValue('http://127.0.0.1:18080/flowgram/demo/mock/weather'),
          },
          paramsValues: {
            city: refValue('start_0', 'departure'),
            date: refValue('start_0', 'date'),
          },
          headersValues: {},
          timeout: {
            timeout: 3000,
            retryTimes: 1,
          },
          body: {
            bodyType: 'none',
          },
          outputs: objectSchema(
            {
              body: stringSchema(),
              statusCode: numberSchema(200),
              headers: objectSchema({}),
            },
            ['body']
          ),
        },
      },
      {
        id: 'tool_0',
        type: WorkflowNodeType.Tool,
        meta: {
          position: {
            x: 1100,
            y: 220,
          },
        },
        data: {
          title: '车次工具',
          inputs: objectSchema(
            {
              toolName: stringSchema('queryTrainInfo'),
              argumentsJson: stringSchema('{"type":40}'),
            },
            ['toolName']
          ),
          inputsValues: {
            toolName: constantValue('queryTrainInfo'),
            argumentsJson: templateValue('{"type":40}'),
          },
          outputs: objectSchema({
            result: stringSchema(),
            rawOutput: stringSchema(),
          }),
        },
      },
      {
        id: 'code_0',
        type: WorkflowNodeType.Code,
        meta: {
          position: {
            x: 1440,
            y: 220,
          },
        },
        data: {
          title: '整流脚本',
          inputs: objectSchema(
            {
              departure: stringSchema(),
              destination: stringSchema(),
              date: stringSchema(),
              trainType: stringSchema(),
              userGoal: stringSchema(),
              routeLabel: stringSchema(),
              travelIntent: stringSchema(),
              weatherBody: stringSchema(),
              toolResult: stringSchema(),
            },
            ['departure', 'destination', 'date', 'trainType', 'userGoal', 'weatherBody']
          ),
          inputsValues: {
            departure: refValue('start_0', 'departure'),
            destination: refValue('start_0', 'destination'),
            date: refValue('start_0', 'date'),
            trainType: refValue('start_0', 'trainType'),
            userGoal: refValue('start_0', 'userGoal'),
            routeLabel: refValue('variable_0', 'routeLabel'),
            travelIntent: refValue('variable_0', 'travelIntent'),
            weatherBody: refValue('http_0', 'body'),
            toolResult: refValue('tool_0', 'result'),
          },
          script: {
            language: 'javascript',
            content:
              "// Merge weather, tool output and user intent into a structured LLM prompt.\n" +
              "function main(input) {\n" +
              "  var params = input && input.params ? input.params : {};\n" +
              "  var weather = {};\n" +
              "  try {\n" +
              "    weather = params.weatherBody ? JSON.parse(params.weatherBody) : {};\n" +
              "  } catch (error) {\n" +
              "    weather = { weather: '未知', temperature: '--', advice: '请手动确认天气' };\n" +
              "  }\n" +
              "  var weatherSummary = [\n" +
              "    params.departure || '',\n" +
              "    (weather.date || params.date || ''),\n" +
              "    (weather.weather || '未知天气'),\n" +
              "    (weather.temperature || '温度待确认'),\n" +
              "    (weather.advice || '请关注出行提醒')\n" +
              "  ].join(' | ');\n" +
              "  var rawTrain = String(params.toolResult || '').replace(/\\s+/g, ' ').trim();\n" +
              "  var trainSummary = rawTrain ? rawTrain.slice(0, 96) : '暂未获取到车次摘要';\n" +
              "  var travelBrief = '路线：' + (params.routeLabel || '') + '；偏好：' + (params.userGoal || '');\n" +
              "  var finalPrompt = [\n" +
              "    '你是一名中文出行顾问。',\n" +
              "    '请根据以下信息给出简短、可执行的建议，控制在三段以内。',\n" +
              "    '路线：' + (params.routeLabel || ''),\n" +
              "    '出发日期：' + (params.date || ''),\n" +
              "    '交通偏好：' + (params.trainType || ''),\n" +
              "    '用户目标：' + (params.userGoal || ''),\n" +
              "    '天气：' + weatherSummary,\n" +
              "    '车次参考：' + trainSummary,\n" +
              "    '请输出：1) 一句话结论 2) 出发建议 3) 注意事项'\n" +
              "  ].join('\\n');\n" +
              "  return {\n" +
              "    weatherSummary: weatherSummary,\n" +
              "    trainSummary: trainSummary,\n" +
              "    travelBrief: travelBrief,\n" +
              "    finalPrompt: finalPrompt\n" +
              "  };\n" +
              "}",
          },
          outputs: objectSchema(
            {
              weatherSummary: stringSchema(),
              trainSummary: stringSchema(),
              travelBrief: stringSchema(),
              finalPrompt: stringSchema(),
            },
            ['weatherSummary', 'trainSummary', 'travelBrief', 'finalPrompt']
          ),
        },
      },
      {
        id: 'llm_0',
        type: WorkflowNodeType.LLM,
        meta: {
          position: {
            x: 1780,
            y: 220,
          },
        },
        data: {
          title: '出行建议',
          inputs: objectSchema(
            {
              serviceId: stringSchema('minimax-coding'),
              modelName: stringSchema('MiniMax-M2.1'),
              systemPrompt: stringSchema(
                '你是一个简洁、可靠的中文出行助手，只输出和行程建议相关的内容。'
              ),
              prompt: stringSchema(),
            },
            ['modelName', 'prompt']
          ),
          outputs: objectSchema(
            {
              result: stringSchema(),
            },
            ['result']
          ),
          inputsValues: {
            serviceId: refValue('variable_0', 'serviceId'),
            modelName: refValue('variable_0', 'modelName'),
            systemPrompt: templateValue(
              '你是一个简洁、可靠的中文出行助手，只输出和行程建议相关的内容。'
            ),
            prompt: refValue('code_0', 'finalPrompt'),
          },
        },
      },
      {
        id: 'end_0',
        type: WorkflowNodeType.End,
        meta: {
          position: {
            x: 2140,
            y: 220,
          },
        },
        data: {
          title: 'End',
          inputs: objectSchema(
            {
              result: stringSchema(),
              weatherSummary: stringSchema(),
              trainSummary: stringSchema(),
              travelBrief: stringSchema(),
            },
            ['result', 'weatherSummary', 'trainSummary', 'travelBrief']
          ),
          inputsValues: {
            result: refValue('llm_0', 'result'),
            weatherSummary: refValue('code_0', 'weatherSummary'),
            trainSummary: refValue('code_0', 'trainSummary'),
            travelBrief: refValue('code_0', 'travelBrief'),
          },
        },
      },
    ],
    edges: [
      {
        sourceNodeID: 'start_0',
        targetNodeID: 'variable_0',
      },
      {
        sourceNodeID: 'variable_0',
        targetNodeID: 'http_0',
      },
      {
        sourceNodeID: 'http_0',
        targetNodeID: 'tool_0',
      },
      {
        sourceNodeID: 'tool_0',
        targetNodeID: 'code_0',
      },
      {
        sourceNodeID: 'code_0',
        targetNodeID: 'llm_0',
      },
      {
        sourceNodeID: 'llm_0',
        targetNodeID: 'end_0',
      },
    ],
  },
};

const conditionTemplate: WorkflowTemplate = {
  id: 'condition-review',
  name: '条件分支',
  description: '演示 Condition 节点如何按输入条件走不同路径。',
  badge: 'Branch',
  inputs: {
    score: 75,
  },
  document: {
    nodes: [
      {
        id: 'start_0',
        type: WorkflowNodeType.Start,
        meta: {
          position: {
            x: 120,
            y: 260,
          },
        },
        data: {
          title: 'Start',
          outputs: objectSchema(
            {
              score: numberSchema(75),
            },
            ['score']
          ),
        },
      },
      {
        id: 'condition_0',
        type: WorkflowNodeType.Condition,
        meta: {
          position: {
            x: 480,
            y: 220,
          },
        },
        data: {
          title: 'Condition',
          conditions: [
            {
              key: 'if_pass',
              value: {
                left: refValue('start_0', 'score'),
                operator: 'gte',
                right: constantValue(60),
              },
            },
          ],
        },
      },
      {
        id: 'end_pass',
        type: WorkflowNodeType.End,
        meta: {
          position: {
            x: 860,
            y: 120,
          },
        },
        data: {
          title: 'End Pass',
          inputs: objectSchema(
            {
              result: stringSchema(),
            },
            ['result']
          ),
          inputsValues: {
            result: constantValue('passed'),
          },
        },
      },
      {
        id: 'end_fail',
        type: WorkflowNodeType.End,
        meta: {
          position: {
            x: 860,
            y: 380,
          },
        },
        data: {
          title: 'End Fail',
          inputs: objectSchema(
            {
              result: stringSchema(),
            },
            ['result']
          ),
          inputsValues: {
            result: constantValue('failed'),
          },
        },
      },
    ],
    edges: [
      {
        sourceNodeID: 'start_0',
        targetNodeID: 'condition_0',
      },
      {
        sourceNodeID: 'condition_0',
        sourcePortID: 'if_pass',
        targetNodeID: 'end_pass',
      },
      {
        sourceNodeID: 'condition_0',
        sourcePortID: 'else',
        targetNodeID: 'end_fail',
      },
    ],
  },
};

const loopTemplate: WorkflowTemplate = {
  id: 'loop-digest',
  name: '循环汇总',
  description: '演示 Loop 节点如何批量处理并聚合输出。',
  badge: 'Loop',
  inputs: {
    cities: ['beijing', 'shanghai'],
  },
  document: {
    nodes: [
      {
        id: 'start_0',
        type: WorkflowNodeType.Start,
        meta: {
          position: {
            x: 120,
            y: 260,
          },
        },
        data: {
          title: 'Start',
          outputs: objectSchema(
            {
              cities: arraySchema(stringSchema(), ['beijing', 'shanghai']),
            },
            ['cities']
          ),
        },
      },
      {
        id: 'loop_0',
        type: WorkflowNodeType.Loop,
        meta: {
          position: {
            x: 480,
            y: 150,
          },
        },
        data: {
          title: 'Loop Cities',
          loopFor: refValue('start_0', 'cities'),
          outputs: objectSchema(
            {
              suggestions: arraySchema(stringSchema()),
            },
            ['suggestions']
          ),
          loopOutputs: {
            suggestions: refValue('llm_loop_0', 'result'),
          },
        },
        blocks: [
          {
            id: 'block_start_loop_0',
            type: WorkflowNodeType.BlockStart,
            meta: {
              position: {
                x: 24,
                y: 72,
              },
            },
            data: {},
          },
          {
            id: 'llm_loop_0',
            type: WorkflowNodeType.LLM,
            meta: {
              position: {
                x: 240,
                y: 24,
              },
            },
            data: {
              title: 'LLM Inside Loop',
              inputs: objectSchema(
                {
                  serviceId: stringSchema('minimax-coding'),
                  modelName: stringSchema('MiniMax-M2.1'),
                  prompt: {
                    ...stringSchema(),
                  },
                },
                ['modelName', 'prompt']
              ),
              outputs: objectSchema(
                {
                  result: stringSchema(),
                },
                ['result']
              ),
              inputsValues: {
                serviceId: constantValue('minimax-coding'),
                modelName: constantValue('MiniMax-M2.1'),
                prompt: templateValue('Summarize {{loop_0_locals.item}} in one short phrase.'),
              },
            },
          },
          {
            id: 'block_end_loop_0',
            type: WorkflowNodeType.BlockEnd,
            meta: {
              position: {
                x: 520,
                y: 72,
              },
            },
            data: {},
          },
        ],
        edges: [
          {
            sourceNodeID: 'block_start_loop_0',
            targetNodeID: 'llm_loop_0',
          },
          {
            sourceNodeID: 'llm_loop_0',
            targetNodeID: 'block_end_loop_0',
          },
        ],
      },
      {
        id: 'end_0',
        type: WorkflowNodeType.End,
        meta: {
          position: {
            x: 980,
            y: 260,
          },
        },
        data: {
          title: 'End',
          inputs: objectSchema(
            {
              result: arraySchema(stringSchema()),
            },
            ['result']
          ),
          inputsValues: {
            result: refValue('loop_0', 'suggestions'),
          },
        },
      },
    ],
    edges: [
      {
        sourceNodeID: 'start_0',
        targetNodeID: 'loop_0',
      },
      {
        sourceNodeID: 'loop_0',
        targetNodeID: 'end_0',
      },
    ],
  },
};

const knowledgeTemplate: WorkflowTemplate = {
  id: 'knowledge-qa',
  name: '知识问答',
  description: '演示 Knowledge 节点接入向量检索后的问答链路。',
  badge: 'Optional',
  inputs: {
    question: '请总结产品退款规则的要点',
  },
  document: {
    nodes: [
      {
        id: 'start_0',
        type: WorkflowNodeType.Start,
        meta: {
          position: {
            x: 120,
            y: 240,
          },
        },
        data: {
          title: 'Start',
          outputs: objectSchema(
            {
              question: stringSchema('请总结产品退款规则的要点'),
            },
            ['question']
          ),
        },
      },
      {
        id: 'knowledge_0',
        type: WorkflowNodeType.Knowledge,
        meta: {
          position: {
            x: 520,
            y: 180,
          },
        },
        data: {
          title: '知识检索',
          inputs: objectSchema(
            {
              serviceId: stringSchema('glm-coding'),
              embeddingModel: stringSchema('embedding-3'),
              namespace: stringSchema('default'),
              query: stringSchema(),
              topK: numberSchema(3),
              delimiter: stringSchema('\n\n'),
            },
            ['serviceId', 'embeddingModel', 'namespace', 'query']
          ),
          inputsValues: {
            serviceId: constantValue('glm-coding'),
            embeddingModel: constantValue('embedding-3'),
            namespace: constantValue('default'),
            query: refValue('start_0', 'question'),
            topK: constantValue(3),
            delimiter: constantValue('\n\n'),
          },
          outputs: objectSchema({
            context: stringSchema(),
            count: numberSchema(0),
            matches: arraySchema(objectSchema({})),
          }),
        },
      },
      {
        id: 'llm_0',
        type: WorkflowNodeType.LLM,
        meta: {
          position: {
            x: 940,
            y: 180,
          },
        },
        data: {
          title: '知识回答',
          inputs: objectSchema(
            {
              serviceId: stringSchema('minimax-coding'),
              modelName: stringSchema('MiniMax-M2.1'),
              systemPrompt: stringSchema('请只依据提供的知识上下文回答问题。'),
              prompt: stringSchema(),
            },
            ['modelName', 'prompt']
          ),
          inputsValues: {
            serviceId: constantValue('minimax-coding'),
            modelName: constantValue('MiniMax-M2.1'),
            systemPrompt: templateValue('请只依据提供的知识上下文回答问题。'),
            prompt: templateValue(
              '问题：{{start_0.question}}\n\n知识上下文：{{knowledge_0.context}}\n\n请给出简洁回答。'
            ),
          },
          outputs: objectSchema(
            {
              result: stringSchema(),
            },
            ['result']
          ),
        },
      },
      {
        id: 'end_0',
        type: WorkflowNodeType.End,
        meta: {
          position: {
            x: 1340,
            y: 240,
          },
        },
        data: {
          title: 'End',
          inputs: objectSchema(
            {
              result: stringSchema(),
              context: stringSchema(),
            },
            ['result']
          ),
          inputsValues: {
            result: refValue('llm_0', 'result'),
            context: refValue('knowledge_0', 'context'),
          },
        },
      },
    ],
    edges: [
      {
        sourceNodeID: 'start_0',
        targetNodeID: 'knowledge_0',
      },
      {
        sourceNodeID: 'knowledge_0',
        targetNodeID: 'llm_0',
      },
      {
        sourceNodeID: 'llm_0',
        targetNodeID: 'end_0',
      },
    ],
  },
};

export const workflowTemplates: WorkflowTemplate[] = [
  blankCanvasTemplate,
  travelCopilotTemplate,
  conditionTemplate,
  loopTemplate,
  knowledgeTemplate,
];

export const blankWorkflowTemplateId = blankCanvasTemplate.id;
export const defaultWorkflowTemplateId = travelCopilotTemplate.id;

export const getWorkflowTemplate = (templateId?: string): WorkflowTemplate =>
  workflowTemplates.find((template) => template.id === templateId) ?? travelCopilotTemplate;

export const cloneWorkflowTemplate = (templateId?: string): WorkflowTemplate => {
  const template = getWorkflowTemplate(templateId);
  return {
    ...template,
    inputs: clone(template.inputs),
    document: clone(template.document),
  };
};
