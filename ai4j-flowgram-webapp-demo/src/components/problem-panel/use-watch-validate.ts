/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useCallback, useEffect, useState } from 'react';

import { debounce } from 'lodash-es';
import { useService, WorkflowDocument } from '@flowgram.ai/free-layout-editor';

import { ValidateService, type ValidateResult } from '../../services/validate-service';

const DEBOUNCE_TIME = 1000;

export const useWatchValidate = () => {
  const [results, setResults] = useState<ValidateResult[]>([]);
  const [loading, setLoading] = useState(false);

  const validateService = useService(ValidateService);
  const workflowDocument = useService(WorkflowDocument);

  const debounceValidate = useCallback(
    debounce(async () => {
      const res = await validateService.validateNodes();
      validateService.validateLines();
      setResults(res);
      setLoading(false);
    }, DEBOUNCE_TIME),
    [validateService]
  );

  const validate = () => {
    setLoading(true);
    debounceValidate();
  };

  useEffect(() => {
    validate();
    const disposable = workflowDocument.onContentChange(() => {
      validate();
    });
    return () => disposable.dispose();
  }, []);

  return { results, loading };
};
