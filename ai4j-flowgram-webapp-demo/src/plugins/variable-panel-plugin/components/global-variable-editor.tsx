/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useEffect } from 'react';

import {
  BaseVariableField,
  GlobalScope,
  useRefresh,
  useService,
} from '@flowgram.ai/free-layout-editor';
import { JsonSchemaEditor, JsonSchemaUtils } from '@flowgram.ai/form-materials';

export function GlobalVariableEditor() {
  const globalScope = useService(GlobalScope);

  const refresh = useRefresh();

  const globalVar = globalScope.getVar() as BaseVariableField;

  useEffect(() => {
    const disposable = globalScope.output.onVariableListChange(() => {
      refresh();
    });

    return () => {
      disposable.dispose();
    };
  }, []);

  if (!globalVar) {
    return null;
  }

  const value = globalVar.type ? JsonSchemaUtils.astToSchema(globalVar.type) : { type: 'object' };

  return (
    <JsonSchemaEditor
      value={value}
      onChange={(_schema) => globalVar.updateType(JsonSchemaUtils.schemaToAST(_schema))}
    />
  );
}
