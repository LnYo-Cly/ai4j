/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import type { FC, ReactNode, MouseEvent, CSSProperties, TouchEvent } from 'react';

import { useWatch } from '@flowgram.ai/free-layout-editor';

import { GroupField } from '../constant';
import { defaultColor, groupColors } from '../color';

interface GroupHeaderProps {
  onDrag: (e: MouseEvent | TouchEvent) => void;
  onFocus: () => void;
  onBlur: () => void;
  children: ReactNode;
  style?: CSSProperties;
}

export const GroupHeader: FC<GroupHeaderProps> = ({ onDrag, onFocus, onBlur, children, style }) => {
  const colorName = useWatch<string>(GroupField.Color) ?? defaultColor;
  const color = groupColors[colorName];
  return (
    <div
      className="workflow-group-header"
      data-flow-editor-selectable="false"
      onMouseDown={onDrag}
      onTouchStart={onDrag}
      onFocus={onFocus}
      onBlur={onBlur}
      style={{
        ...style,
        backgroundColor: color['50'],
        borderColor: color['300'],
      }}
    >
      {children}
    </div>
  );
};
