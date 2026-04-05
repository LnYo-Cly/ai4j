/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconLLM from '../../assets/icon-llm.jpg';

let index = 0;
export const LLMNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.LLM,
  info: {
    icon: iconLLM,
    description:
      'Call the large language model and use variables and prompt words to generate responses.',
  },
  meta: {
    size: {
      width: 360,
      height: 390,
    },
  },
  onAdd() {
    return {
      id: `llm_${nanoid(5)}`,
      type: 'llm',
      data: {
        title: `LLM_${++index}`,
        inputsValues: {
          serviceId: {
            type: 'constant',
            content: 'minimax-coding',
          },
          modelName: {
            type: 'constant',
            content: 'MiniMax-M2.1',
          },
          systemPrompt: {
            type: 'template',
            content: 'You are a concise assistant that follows the user exactly.',
          },
          prompt: {
            type: 'template',
            content: '',
          },
        },
        inputs: {
          type: 'object',
          required: ['modelName', 'prompt'],
          properties: {
            serviceId: {
              type: 'string',
            },
            modelName: {
              type: 'string',
            },
            systemPrompt: {
              type: 'string',
            },
            prompt: {
              type: 'string',
            },
          },
        },
        outputs: {
          type: 'object',
          required: ['result'],
          properties: {
            result: { type: 'string' },
          },
        },
      },
    };
  },
};
