/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { FC } from 'react';

import { WorkflowNodeEntity } from '@flowgram.ai/free-layout-editor';

import { NodeMenu } from '../../node-menu';

interface IMoreButton {
  node: WorkflowNodeEntity;
  focused: boolean;
  deleteNode: () => void;
}

export const MoreButton: FC<IMoreButton> = ({ node, focused, deleteNode }) => (
  <div
    className={`workflow-comment-more-button ${
      focused ? 'workflow-comment-more-button-focused' : ''
    }`}
  >
    <NodeMenu node={node} deleteNode={deleteNode} />
  </div>
);
