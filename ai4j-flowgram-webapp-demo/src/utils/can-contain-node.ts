/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { type FlowNodeType } from '@flowgram.ai/free-layout-editor';

import { WorkflowNodeType } from '../nodes';

/**
 * 判断父节点是否可以包含对应子节点
 * Determine whether the parent node can contain the corresponding child node
 * @param childNodeType
 * @param parentNodeType
 */
export function canContainNode(
  childNodeType: WorkflowNodeType | FlowNodeType,
  parentNodeType: WorkflowNodeType | FlowNodeType
) {
  /**
   * 开始/结束节点无法更改容器
   * The start and end nodes cannot change container
   */
  if (
    [
      WorkflowNodeType.Start,
      WorkflowNodeType.End,
      WorkflowNodeType.BlockStart,
      WorkflowNodeType.BlockEnd,
    ].includes(childNodeType as WorkflowNodeType)
  ) {
    return false;
  }
  /**
   * 继续循环与终止循环只能在循环节点中
   * Continue loop and break loop can only be in loop nodes
   */
  if (
    [WorkflowNodeType.Continue, WorkflowNodeType.Break].includes(
      childNodeType as WorkflowNodeType
    ) &&
    parentNodeType !== WorkflowNodeType.Loop
  ) {
    return false;
  }
  /**
   * 循环节点无法嵌套循环节点
   * Loop node cannot nest loop node
   */
  if (childNodeType === WorkflowNodeType.Loop && parentNodeType === WorkflowNodeType.Loop) {
    return false;
  }
  return true;
}
