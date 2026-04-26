/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useCallback } from 'react';

import { usePlayground, usePlaygroundTools } from '@flowgram.ai/free-layout-editor';
import { IconButton, Tooltip } from '@douyinfe/semi-ui';

import { IconAutoLayout } from '../../assets/icon-auto-layout';

export const AutoLayout = () => {
  const tools = usePlaygroundTools();
  const playground = usePlayground();
  const autoLayout = useCallback(async () => {
    await tools.autoLayout({
      enableAnimation: true,
      animationDuration: 1000,
      layoutConfig: {
        rankdir: 'LR',
        align: undefined,
        nodesep: 100,
        ranksep: 100,
      },
    });
  }, [tools]);

  return (
    <Tooltip content={'Auto Layout'}>
      <IconButton
        disabled={playground.config.readonly}
        type="tertiary"
        theme="borderless"
        onClick={autoLayout}
        icon={IconAutoLayout}
      />
    </Tooltip>
  );
};
