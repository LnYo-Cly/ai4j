/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useContext } from 'react';

import { IsSidebarContext } from '../context';

export function useIsSidebar() {
  return useContext(IsSidebarContext);
}
