/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconCode from '../../assets/icon-script.png';
import { formMeta } from './form-meta';

let index = 0;

const defaultCode = `// FlowGram injects upstream values into input.params.
// Return a plain JSON-serializable object from main().
// Current MVP supports synchronous JavaScript only.

function main(input) {
  var params = input && input.params ? input.params : {};
  var value = String(params.input || '');

  return {
    result: value + value,
  };
}`;

export const CodeNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Code,
  info: {
    icon: iconCode,
    description: 'Run synchronous JavaScript against upstream inputs.',
  },
  meta: {
    size: {
      width: 360,
      height: 390,
    },
  },
  onAdd() {
    return {
      id: `code_${nanoid(5)}`,
      type: 'code',
      data: {
        title: `Code_${++index}`,
        inputsValues: {
          input: { type: 'constant', content: '' },
        },
        script: {
          language: 'javascript',
          content: defaultCode,
        },
        outputs: {
          type: 'object',
          required: ['result'],
          properties: {
            result: {
              type: 'string',
            },
          },
        },
      },
    };
  },
  formMeta: formMeta,
};
