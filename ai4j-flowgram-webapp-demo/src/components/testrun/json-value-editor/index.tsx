/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useEffect, useMemo, useRef, useState } from 'react';

import { TextArea } from '@douyinfe/semi-ui';

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const formatJsonValue = (value: unknown, kind: 'object' | 'array') => {
  const fallbackValue = kind === 'array' ? [] : {};
  return JSON.stringify(value ?? fallbackValue, null, 2);
};

export function JsonValueEditor({
  value,
  kind,
  onChange,
}: {
  value: unknown;
  kind: 'object' | 'array';
  onChange: (value: unknown) => void;
}) {
  const defaultJsonText = useMemo(() => formatJsonValue(value, kind), [kind, value]);

  const [jsonText, setJsonText] = useState(defaultJsonText);
  const [parseError, setParseError] = useState<string>();

  const effectVersion = useRef(0);
  const changeVersion = useRef(0);

  const handleJsonTextChange = (text: string) => {
    setJsonText(text);
    try {
      const jsonValue = JSON.parse(text);
      const typeMatches = kind === 'array' ? Array.isArray(jsonValue) : isRecord(jsonValue);
      if (!typeMatches) {
        setParseError(`JSON input must be a ${kind}.`);
        return;
      }
      onChange(jsonValue);
      changeVersion.current++;
      setParseError(undefined);
    } catch (error) {
      setParseError('JSON parse failed. Please check syntax.');
    }
  };

  useEffect(() => {
    // more effect compared with change
    effectVersion.current = effectVersion.current + 1;
    if (effectVersion.current === changeVersion.current) {
      return;
    }
    effectVersion.current = changeVersion.current;

    setJsonText(formatJsonValue(value, kind));
    setParseError(undefined);
  }, [kind, value]);

  return (
    <>
      <TextArea
        value={jsonText}
        onChange={handleJsonTextChange}
        autosize={{
          minRows: 6,
          maxRows: 12,
        }}
      />
      {parseError ? <div style={{ color: '#c93c3c', fontSize: 12, marginTop: 8 }}>{parseError}</div> : null}
    </>
  );
}
