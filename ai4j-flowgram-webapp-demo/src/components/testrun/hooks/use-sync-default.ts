/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useEffect } from 'react';

import { TestRunFormMeta, TestRunFormMetaItem } from '../testrun-form/type';

const getDefaultValue = (meta: TestRunFormMetaItem) => {
  if (['object', 'array', 'map'].includes(meta.type) && typeof meta.defaultValue === 'string') {
    return JSON.parse(meta.defaultValue);
  }
  return meta.defaultValue;
};

export const useSyncDefault = (params: {
  formMeta: TestRunFormMeta;
  values: Record<string, unknown>;
  setValues: (values: Record<string, unknown>) => void;
}) => {
  const { formMeta, values, setValues } = params;

  useEffect(() => {
    let formMetaValues: Record<string, unknown> = {};
    formMeta.map((meta) => {
      // If there is no value in values but there is a default value, trigger onChange once
      if (!(meta.name in values) && meta.defaultValue !== undefined) {
        formMetaValues = { ...formMetaValues, [meta.name]: getDefaultValue(meta) };
      }
    });
    setValues({
      ...values,
      ...formMetaValues,
    });
  }, [formMeta]);
};
