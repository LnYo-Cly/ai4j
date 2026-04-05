/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { FlowNodeRegistry } from '../typings';
import { StartNodeRegistry } from './start';
import { LoopNodeRegistry } from './loop';
import { LLMNodeRegistry } from './llm';
import { HTTPNodeRegistry } from './http';
import { CodeNodeRegistry } from './code';
import { ToolNodeRegistry } from './tool';
import { KnowledgeNodeRegistry } from './knowledge';
import { VariableNodeRegistry } from './variable';
import { EndNodeRegistry } from './end';
import { ConditionNodeRegistry } from './condition';
import { BlockStartNodeRegistry } from './block-start';
import { BlockEndNodeRegistry } from './block-end';
export { WorkflowNodeType } from './constants';

export const nodeRegistries: FlowNodeRegistry[] = [
  ConditionNodeRegistry,
  StartNodeRegistry,
  EndNodeRegistry,
  LLMNodeRegistry,
  HTTPNodeRegistry,
  CodeNodeRegistry,
  ToolNodeRegistry,
  KnowledgeNodeRegistry,
  VariableNodeRegistry,
  LoopNodeRegistry,
  BlockStartNodeRegistry,
  BlockEndNodeRegistry,
];
