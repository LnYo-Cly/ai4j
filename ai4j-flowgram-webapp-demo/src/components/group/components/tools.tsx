/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { FC } from 'react';

import { IconHandle } from '@douyinfe/semi-icons';

import { GroupTitle } from './title';
import { GroupColor } from './color';

export const GroupTools: FC = () => (
  <div className="workflow-group-tools">
    <IconHandle className="workflow-group-tools-drag" />
    <GroupTitle />
    <GroupColor />
  </div>
);
