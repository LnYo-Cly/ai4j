/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { CSSProperties, MouseEvent, TouchEvent, type FC } from 'react';

import { useNodeRender, usePlayground } from '@flowgram.ai/free-layout-editor';

import { type CommentEditorModel } from '../model';

interface IDragArea {
  model: CommentEditorModel;
  stopEvent?: boolean;
  style?: CSSProperties;
}

export const DragArea: FC<IDragArea> = (props) => {
  const { model, stopEvent = true, style } = props;

  const playground = usePlayground();

  const { startDrag: onStartDrag, onFocus, onBlur, selectNode } = useNodeRender();

  const handleDrag = (e: MouseEvent | TouchEvent) => {
    if (stopEvent) {
      e.preventDefault();
      e.stopPropagation();
    }
    model.setFocus(false);
    onStartDrag(e as MouseEvent);
    selectNode(e as MouseEvent);
    playground.node.focus(); // 防止节点无法被删除
  };

  return (
    <div
      className="workflow-comment-drag-area"
      data-flow-editor-selectable="false"
      draggable={true}
      style={style}
      onMouseDown={handleDrag}
      onTouchStart={handleDrag}
      onFocus={onFocus}
      onBlur={onBlur}
    />
  );
};
