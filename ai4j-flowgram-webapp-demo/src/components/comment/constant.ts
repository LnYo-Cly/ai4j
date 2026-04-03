/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

/* eslint-disable @typescript-eslint/naming-convention -- enum */

export enum CommentEditorFormField {
  Size = 'size',
  Note = 'note',
}

/** 编辑器事件 */
export enum CommentEditorEvent {
  /** 初始化事件 */
  Init = 'init',
  /** 内容变更事件 */
  Change = 'change',
  /** 多选事件 */
  MultiSelect = 'multiSelect',
  /** 单选事件 */
  Select = 'select',
  /** 失焦事件 */
  Blur = 'blur',
}

export const CommentEditorDefaultValue = '';
