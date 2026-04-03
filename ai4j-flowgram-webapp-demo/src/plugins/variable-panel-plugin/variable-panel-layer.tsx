/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { domUtils, injectable, Layer } from '@flowgram.ai/free-layout-editor';

import { VariablePanel } from './components/variable-panel';

@injectable()
export class VariablePanelLayer extends Layer {
  onReady(): void {
    // Fix variable panel in the right of canvas
    this.config.onDataChange(() => {
      const { scrollX, scrollY } = this.config.config;
      domUtils.setStyle(this.node, {
        position: 'absolute',
        right: 25 - scrollX,
        top: scrollY + 25,
      });
    });
  }

  render(): JSX.Element {
    return <VariablePanel />;
  }
}
