import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconTool from '../../assets/icon-tool.svg';
import { defaultFormMeta } from '../default-form-meta';

let index = 0;

export const ToolNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Tool,
  info: {
    icon: iconTool,
    description: 'Invoke a local ai4j tool or MCP-exposed function.',
  },
  meta: {
    size: {
      width: 360,
      height: 360,
    },
  },
  onAdd() {
    return {
      id: `tool_${nanoid(5)}`,
      type: WorkflowNodeType.Tool,
      data: {
        title: `Tool_${++index}`,
        inputsValues: {
          toolName: {
            type: 'constant',
            content: 'queryTrainInfo',
          },
          argumentsJson: {
            type: 'template',
            content: '{"type":40}',
          },
        },
        inputs: {
          type: 'object',
          required: ['toolName'],
          properties: {
            toolName: {
              type: 'string',
            },
            argumentsJson: {
              type: 'string',
            },
          },
        },
        outputs: {
          type: 'object',
          properties: {
            result: {
              type: 'string',
            },
            rawOutput: {
              type: 'string',
            },
          },
        },
      },
    };
  },
  formMeta: defaultFormMeta,
};
