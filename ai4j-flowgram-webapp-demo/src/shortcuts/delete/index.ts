/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import {
  FreeLayoutPluginContext,
  ShortcutsHandler,
  WorkflowDocument,
  WorkflowLineEntity,
  WorkflowNodeEntity,
  WorkflowNodeMeta,
  WorkflowSelectService,
  HistoryService,
  PlaygroundConfigEntity,
} from '@flowgram.ai/free-layout-editor';
import { Toast } from '@douyinfe/semi-ui';

import { FlowCommandId } from '../constants';
import { WorkflowNodeType } from '../../nodes';

export class DeleteShortcut implements ShortcutsHandler {
  public commandId = FlowCommandId.DELETE;

  public shortcuts = ['backspace', 'delete'];

  private playgroundConfig: PlaygroundConfigEntity;

  private document: WorkflowDocument;

  private selectService: WorkflowSelectService;

  private historyService: HistoryService;

  /**
   * initialize delete shortcut - 初始化删除快捷键
   */
  constructor(context: FreeLayoutPluginContext) {
    this.playgroundConfig = context.playground.config;
    this.document = context.get(WorkflowDocument);
    this.selectService = context.get(WorkflowSelectService);
    this.historyService = context.get(HistoryService);
    this.execute = this.execute.bind(this);
  }

  /**
   * execute delete operation - 执行删除操作
   */
  public async execute(nodes?: WorkflowNodeEntity[]): Promise<void> {
    if (this.readonly) {
      return;
    }
    const selection = Array.isArray(nodes) ? nodes : this.selectService.selection;
    if (
      !this.isValid(
        selection.filter((n) => n instanceof WorkflowNodeEntity) as WorkflowNodeEntity[]
      )
    ) {
      return;
    }
    // Merge actions to redo/undo
    this.historyService.startTransaction();
    // delete selected entities - 删除选中实体
    selection.forEach((entity) => {
      if (entity instanceof WorkflowNodeEntity) {
        this.removeNode(entity);
      } else if (entity instanceof WorkflowLineEntity) {
        this.removeLine(entity);
      } else {
        entity.dispose();
      }
    });
    // filter out disposed entities - 过滤掉已删除的实体
    this.selectService.selection = this.selectService.selection.filter((s) => !s.disposed);
    this.historyService.endTransaction();
  }

  /**
   * readonly - 是否只读
   */
  private get readonly(): boolean {
    return this.playgroundConfig.readonly;
  }

  /**
   * validate if nodes can be deleted - 验证节点是否可以删除
   */
  private isValid(nodes: WorkflowNodeEntity[]): boolean {
    const hasSystemNodes = nodes.some((n) =>
      [WorkflowNodeType.Start, WorkflowNodeType.End].includes(n.flowNodeType as WorkflowNodeType)
    );
    if (hasSystemNodes) {
      Toast.error({
        content: 'Start or End node cannot be deleted',
        showClose: false,
      });
      return false;
    }
    return true;
  }

  /**
   * remove node from workflow - 从工作流中删除节点
   */
  private removeNode(node: WorkflowNodeEntity): void {
    if (!this.document.canRemove(node)) {
      return;
    }
    const nodeMeta = node.getNodeMeta<WorkflowNodeMeta>();
    const subCanvas = nodeMeta.subCanvas?.(node);
    if (subCanvas?.isCanvas) {
      subCanvas.parentNode.dispose();
      return;
    }
    node.dispose();
  }

  /**
   * remove line from workflow - 从工作流中删除连线
   */
  private removeLine(line: WorkflowLineEntity): void {
    if (!this.document.linesManager.canRemove(line)) {
      return;
    }
    line.dispose();
  }
}
