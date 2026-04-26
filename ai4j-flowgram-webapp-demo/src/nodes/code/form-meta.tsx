/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { FormMeta, FormRenderProps } from '@flowgram.ai/free-layout-editor';
import { createInferInputsPlugin } from '@flowgram.ai/form-materials';

import { FormHeader, FormContent } from '../../form-components';
import { CodeNodeJSON } from './types';
import { Outputs } from './components/outputs';
import { Inputs } from './components/inputs';
import { Code } from './components/code';
import { defaultFormMeta } from '../default-form-meta';

export const FormRender = ({ form }: FormRenderProps<CodeNodeJSON>) => (
  <>
    <FormHeader />
    <FormContent>
      <Inputs />
      <Code />
      <Outputs />
    </FormContent>
  </>
);

export const formMeta: FormMeta = {
  render: (props) => <FormRender {...props} />,
  effect: defaultFormMeta.effect,
  validate: defaultFormMeta.validate,
  plugins: [createInferInputsPlugin({ sourceKey: 'inputsValues', targetKey: 'inputs' })],
};
