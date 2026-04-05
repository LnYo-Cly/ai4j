/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import type { JsonSchemaBasicType } from '@flowgram.ai/form-materials';

export interface TestRunFormMetaItem {
  type: JsonSchemaBasicType;
  name: string;
  defaultValue: unknown;
  required: boolean;
  itemsType?: JsonSchemaBasicType;
}

export type TestRunFormMeta = TestRunFormMetaItem[];

export interface TestRunFormField extends TestRunFormMetaItem {
  value: unknown;
  onChange: (value: unknown) => void;
}
