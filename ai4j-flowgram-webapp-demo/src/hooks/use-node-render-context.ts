/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useContext } from 'react';

import { NodeRenderContext } from '../context';

export function useNodeRenderContext() {
  return useContext(NodeRenderContext);
}
