/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconVariable from '../../assets/icon-variable.png';
import { formMeta } from './form-meta';
import { inferVariableOutputsSchema } from './output-schema';

let index = 0;

export const VariableNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Variable,
  info: {
    icon: iconVariable,
    description: 'Variable Assign and Declaration',
  },
  meta: {
    size: {
      width: 360,
      height: 390,
    },
  },
  onAdd() {
    const assign = [
      {
        operator: 'declare' as const,
        left: 'sum',
        right: {
          type: 'constant' as const,
          content: 0,
          schema: { type: 'integer' as const },
        },
      },
    ];

    return {
      id: `variable_${nanoid(5)}`,
      type: 'variable',
      data: {
        title: `Variable_${++index}`,
        assign,
        outputs: inferVariableOutputsSchema(assign, new Map()),
      },
    };
  },
  formMeta: formMeta,
};
