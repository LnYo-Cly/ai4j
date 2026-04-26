/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import React from 'react';

import type { NodeRenderReturnType } from '@flowgram.ai/free-layout-editor';

interface INodeRenderContext extends NodeRenderReturnType {}

/** 业务自定义节点上下文 */
export const NodeRenderContext = React.createContext<INodeRenderContext>({} as INodeRenderContext);
