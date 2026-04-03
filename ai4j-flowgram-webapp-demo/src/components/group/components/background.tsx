/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { CSSProperties, FC, useEffect } from 'react';

import { useWatch, WorkflowNodeEntity } from '@flowgram.ai/free-layout-editor';

import { GroupField } from '../constant';
import { defaultColor, groupColors } from '../color';

interface GroupBackgroundProps {
  node: WorkflowNodeEntity;
  style?: CSSProperties;
  selected: boolean;
}

export const GroupBackground: FC<GroupBackgroundProps> = ({ node, style, selected }) => {
  const colorName = useWatch<string>(GroupField.Color) ?? defaultColor;
  const color = groupColors[colorName];

  useEffect(() => {
    const styleElement = document.createElement('style');

    // 使用独特的选择器
    const styleContent = `
      .workflow-group-render[data-group-id="${node.id}"] .workflow-group-background {
        border: 1px solid ${color['300']};
      }

      .workflow-group-render.selected[data-group-id="${node.id}"] .workflow-group-background {
        border: 1px solid #4e40e5;
      }
    `;

    styleElement.textContent = styleContent;
    document.head.appendChild(styleElement);

    return () => {
      styleElement.remove();
    };
  }, [color]);

  return (
    <div
      className="workflow-group-background"
      data-flow-editor-selectable="true"
      style={{
        ...style,
        backgroundColor: `${color['300']}${selected ? '40' : '29'}`,
      }}
    />
  );
};
