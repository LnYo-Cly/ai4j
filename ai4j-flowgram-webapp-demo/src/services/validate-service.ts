/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import {
  inject,
  injectable,
  WorkflowLinesManager,
  FlowNodeEntity,
  FlowNodeFormData,
  FormModelV2,
  WorkflowDocument,
} from '@flowgram.ai/free-layout-editor';

export interface ValidateResult {
  node: FlowNodeEntity;
  feedbacks: any[];
}

@injectable()
export class ValidateService {
  @inject(WorkflowLinesManager)
  protected readonly linesManager: WorkflowLinesManager;

  @inject(WorkflowDocument) private readonly document: WorkflowDocument;

  validateLines() {
    const allLines = this.linesManager.getAllLines();
    allLines.forEach((line) => line.validate());
  }

  async validateNode(node: FlowNodeEntity) {
    const feedbacks = await node
      .getData(FlowNodeFormData)
      .getFormModel<FormModelV2>()
      .validateWithFeedbacks();
    return feedbacks;
  }

  async validateNodes(): Promise<ValidateResult[]> {
    const nodes = this.document.getAssociatedNodes();
    const results = await Promise.all(
      nodes.map(async (node) => {
        const feedbacks = await this.validateNode(node);
        return {
          feedbacks,
          node,
        };
      })
    );

    return results.filter((i) => i.feedbacks.length);
  }
}
