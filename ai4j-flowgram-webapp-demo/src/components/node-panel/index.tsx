/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useRef } from 'react';

import { NodePanelRenderProps as NodePanelRenderPropsDefault } from '@flowgram.ai/free-node-panel-plugin';
import { WorkflowPortEntity } from '@flowgram.ai/free-layout-editor';
import { Popover } from '@douyinfe/semi-ui';

import { NodePlaceholder } from './node-placeholder';
import { NodeList } from './node-list';
import './index.less';

interface NodePanelRenderProps extends NodePanelRenderPropsDefault {
  panelProps?: {
    fromPort?: WorkflowPortEntity; // 从哪个端口添加 From which port to add
    enableNodePlaceholder?: boolean;
  };
}
export const NodePanel: React.FC<NodePanelRenderProps> = (props) => {
  const { onSelect, position, onClose, containerNode, panelProps = {} } = props;
  const { enableNodePlaceholder, fromPort } = panelProps;
  const ref = useRef<HTMLDivElement>(null);

  return (
    <Popover
      trigger="click"
      visible={true}
      onVisibleChange={(v) => (v ? null : onClose())}
      content={<NodeList onSelect={onSelect} containerNode={containerNode} fromPort={fromPort} />}
      getPopupContainer={containerNode ? () => ref.current || document.body : undefined}
      placement="right"
      popupAlign={{ offset: [30, 0] }}
      overlayStyle={{
        padding: 0,
      }}
    >
      <div
        ref={ref}
        style={
          enableNodePlaceholder
            ? {
                position: 'absolute',
                top: position.y - 61.5,
                left: position.x,
                width: 360,
                height: 100,
              }
            : {
                position: 'absolute',
                top: position.y,
                left: position.x,
                width: 0,
                height: 0,
              }
        }
      >
        {enableNodePlaceholder && <NodePlaceholder />}
      </div>
    </Popover>
  );
};
