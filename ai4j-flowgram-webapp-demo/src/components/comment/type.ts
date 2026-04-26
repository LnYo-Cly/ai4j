/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import type { CommentEditorEvent } from './constant';

interface CommentEditorChangeEvent {
  type: CommentEditorEvent.Change;
  value: string;
}

interface CommentEditorMultiSelectEvent {
  type: CommentEditorEvent.MultiSelect;
}

interface CommentEditorSelectEvent {
  type: CommentEditorEvent.Select;
}

interface CommentEditorBlurEvent {
  type: CommentEditorEvent.Blur;
}

interface CommentEditorInitEvent {
  type: CommentEditorEvent.Init;
  value: string;
}

export type CommentEditorEventParams =
  | CommentEditorChangeEvent
  | CommentEditorMultiSelectEvent
  | CommentEditorSelectEvent
  | CommentEditorBlurEvent
  | CommentEditorInitEvent;
