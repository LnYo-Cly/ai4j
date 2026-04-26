/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { nanoid } from 'nanoid';

import { FlowNodeRegistry } from '../../typings';
import iconBreak from '../../assets/icon-break.jpg';
import { formMeta } from './form-meta';
import { WorkflowNodeType } from '../constants';

let index = 0;
export const BreakNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Break,
  meta: {
    defaultPorts: [{ type: 'input' }],
    sidebarDisabled: true,
    size: {
      width: 360,
      height: 54,
    },
    expandable: false,
    onlyInContainer: WorkflowNodeType.Loop,
  },
  info: {
    icon: iconBreak,
    description:
      'The final node of the workflow, used to return the result information after the workflow is run.',
  },
  /**
   * Render node via formMeta
   */
  formMeta,
  onAdd() {
    return {
      id: `break_${nanoid(5)}`,
      type: 'break',
      data: {
        title: `Break_${++index}`,
      },
    };
  },
};
