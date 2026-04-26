/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { FlowNodeJSON } from '@flowgram.ai/free-layout-editor';
import { AssignValueType, IJsonSchema } from '@flowgram.ai/form-materials';

export interface VariableNodeJSON extends FlowNodeJSON {
  data: {
    title: string;
    assign: AssignValueType[];
    outputs: IJsonSchema<'object'>;
  };
}
