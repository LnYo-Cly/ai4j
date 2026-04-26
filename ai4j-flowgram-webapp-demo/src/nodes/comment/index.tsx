/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';

export const CommentNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Comment,
  meta: {
    sidebarDisabled: true,
    nodePanelVisible: false,
    defaultPorts: [],
    renderKey: WorkflowNodeType.Comment,
    size: {
      width: 240,
      height: 150,
    },
  },
  formMeta: {
    render: () => <></>,
  },
  getInputPoints: () => [], // Comment 节点没有输入
  getOutputPoints: () => [], // Comment 节点没有输出
};
