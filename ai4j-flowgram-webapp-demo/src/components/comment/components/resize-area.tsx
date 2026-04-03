/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { CSSProperties, type FC } from 'react';

import { MouseTouchEvent, useNodeRender, usePlayground } from '@flowgram.ai/free-layout-editor';

import type { CommentEditorModel } from '../model';

interface IResizeArea {
  model: CommentEditorModel;
  onResize?: () => {
    resizing: (delta: { top: number; right: number; bottom: number; left: number }) => void;
    resizeEnd: () => void;
  };
  getDelta?: (delta: { x: number; y: number }) => {
    top: number;
    right: number;
    bottom: number;
    left: number;
  };
  style?: CSSProperties;
}

export const ResizeArea: FC<IResizeArea> = (props) => {
  const { model, onResize, getDelta, style } = props;

  const playground = usePlayground();

  const { selectNode } = useNodeRender();

  const handleResizeStart = (
    startResizeEvent: React.MouseEvent | React.TouchEvent | MouseEvent
  ) => {
    MouseTouchEvent.preventDefault(startResizeEvent);
    startResizeEvent.stopPropagation();
    if (!onResize) {
      return;
    }
    const { resizing, resizeEnd } = onResize();
    model.setFocus(false);
    selectNode(startResizeEvent as React.MouseEvent);
    playground.node.focus(); // 防止节点无法被删除

    const { clientX: startX, clientY: startY } = MouseTouchEvent.getEventCoord(
      startResizeEvent as MouseEvent
    );

    const handleResizing = (mouseMoveEvent: MouseEvent | TouchEvent) => {
      const { clientX: moveX, clientY: moveY } = MouseTouchEvent.getEventCoord(mouseMoveEvent);
      const deltaX = moveX - startX;
      const deltaY = moveY - startY;
      const delta = getDelta?.({ x: deltaX, y: deltaY });
      if (!delta || !resizing) {
        return;
      }
      resizing(delta);
    };

    const handleResizeEnd = () => {
      resizeEnd();
      document.removeEventListener('mousemove', handleResizing);
      document.removeEventListener('mouseup', handleResizeEnd);
      document.removeEventListener('click', handleResizeEnd);
      document.removeEventListener('touchmove', handleResizing);
      document.removeEventListener('touchend', handleResizeEnd);
      document.removeEventListener('touchcancel', handleResizeEnd);
    };

    document.addEventListener('mousemove', handleResizing);
    document.addEventListener('mouseup', handleResizeEnd);
    document.addEventListener('click', handleResizeEnd);
    document.addEventListener('touchmove', handleResizing, { passive: false });
    document.addEventListener('touchend', handleResizeEnd);
    document.addEventListener('touchcancel', handleResizeEnd);
  };

  return (
    <div
      className="workflow-comment-resize-area"
      style={style}
      data-flow-editor-selectable="false"
      onMouseDown={handleResizeStart}
      onTouchStart={handleResizeStart}
    />
  );
};
