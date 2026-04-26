/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { NodePanelResult, WorkflowNodePanelService } from '@flowgram.ai/free-node-panel-plugin';
import {
  Layer,
  injectable,
  inject,
  FreeLayoutPluginContext,
  WorkflowHoverService,
  WorkflowNodeEntity,
  WorkflowNodeJSON,
  WorkflowSelectService,
  WorkflowDocument,
  PositionSchema,
  WorkflowDragService,
} from '@flowgram.ai/free-layout-editor';
import { ContainerUtils } from '@flowgram.ai/free-container-plugin';

@injectable()
export class ContextMenuLayer extends Layer {
  @inject(FreeLayoutPluginContext) ctx: FreeLayoutPluginContext;

  @inject(WorkflowNodePanelService) nodePanelService: WorkflowNodePanelService;

  @inject(WorkflowHoverService) hoverService: WorkflowHoverService;

  @inject(WorkflowSelectService) selectService: WorkflowSelectService;

  @inject(WorkflowDocument) document: WorkflowDocument;

  @inject(WorkflowDragService) dragService: WorkflowDragService;

  onReady() {
    this.listenPlaygroundEvent('contextmenu', (e) => {
      if (this.config.readonlyOrDisabled) return;
      this.openNodePanel(e);
      e.preventDefault();
      e.stopPropagation();
    });
  }

  openNodePanel(e: MouseEvent) {
    const mousePos = this.getPosFromMouseEvent(e);
    const containerNode = this.getContainerNode(mousePos);
    this.nodePanelService.callNodePanel({
      position: mousePos,
      containerNode,
      panelProps: {},
      // handle node selection from panel - 处理从面板中选择节点
      onSelect: async (panelParams?: NodePanelResult) => {
        if (!panelParams) {
          return;
        }
        const { nodeType, nodeJSON } = panelParams;
        const position = this.dragService.adjustSubNodePosition(nodeType, containerNode, mousePos);
        // create new workflow node based on selected type - 根据选择的类型创建新的工作流节点
        const node: WorkflowNodeEntity = this.ctx.document.createWorkflowNodeByType(
          nodeType,
          position,
          nodeJSON ?? ({} as WorkflowNodeJSON),
          containerNode?.id
        );
        // select the newly created node - 选择新创建的节点
        this.selectService.select(node);
      },
      // handle panel close - 处理面板关闭
      onClose: () => {},
    });
  }

  private getContainerNode(mousePos: PositionSchema): WorkflowNodeEntity | undefined {
    const allNodes = this.document.getAllNodes();
    const containerTransforms = ContainerUtils.getContainerTransforms(allNodes);
    const collisionTransform = ContainerUtils.getCollisionTransform({
      targetPoint: mousePos,
      transforms: containerTransforms,
      document: this.document,
    });
    return collisionTransform?.entity;
  }
}
