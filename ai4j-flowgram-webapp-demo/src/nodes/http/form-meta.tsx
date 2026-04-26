/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { FormMeta, FormRenderProps } from '@flowgram.ai/free-layout-editor';
import { createInferInputsPlugin, DisplayOutputs } from '@flowgram.ai/form-materials';
import { Divider } from '@douyinfe/semi-ui';

import { FormHeader, FormContent } from '../../form-components';
import { HTTPNodeJSON } from './types';
import { Timeout } from './components/timeout';
import { Params } from './components/params';
import { Headers } from './components/headers';
import { Body } from './components/body';
import { Api } from './components/api';
import { defaultFormMeta } from '../default-form-meta';

export const FormRender = ({ form }: FormRenderProps<HTTPNodeJSON>) => (
  <>
    <FormHeader />
    <FormContent>
      <Api />
      <Divider />
      <Headers />
      <Divider />
      <Params />
      <Divider />
      <Body />
      <Divider />
      <Timeout />
      <Divider />
      <DisplayOutputs displayFromScope />
    </FormContent>
  </>
);

export const formMeta: FormMeta = {
  render: (props) => <FormRender {...props} />,
  effect: defaultFormMeta.effect,
  plugins: [
    createInferInputsPlugin({ sourceKey: 'headersValues', targetKey: 'headers' }),
    createInferInputsPlugin({ sourceKey: 'paramsValues', targetKey: 'params' }),
  ],
};
