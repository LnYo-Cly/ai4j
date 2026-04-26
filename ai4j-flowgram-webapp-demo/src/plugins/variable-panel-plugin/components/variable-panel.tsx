/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useState } from 'react';

import { Button, Collapsible, Tabs, Tooltip } from '@douyinfe/semi-ui';
import { IconMinus } from '@douyinfe/semi-icons';

import iconVariable from '../../../assets/icon-variable.png';
import { GlobalVariableEditor } from './global-variable-editor';
import { FullVariableList } from './full-variable-list';

import styles from './index.module.less';

export function VariablePanel() {
  const [isOpen, setOpen] = useState<boolean>(false);

  return (
    <div className={styles['panel-wrapper']}>
      <Tooltip content="Toggle Variable Panel">
        <Button
          className={`${styles['variable-panel-button']} ${isOpen ? styles.close : ''}`}
          theme={isOpen ? 'borderless' : 'light'}
          onClick={() => setOpen((_open) => !_open)}
        >
          {isOpen ? <IconMinus /> : <img src={iconVariable} width={20} height={20} />}
        </Button>
      </Tooltip>
      <Collapsible isOpen={isOpen}>
        <div className={styles['panel-container']}>
          <Tabs>
            <Tabs.TabPane itemKey="variables" tab="Variable List">
              <FullVariableList />
            </Tabs.TabPane>
            <Tabs.TabPane itemKey="global" tab="Global Editor">
              <GlobalVariableEditor />
            </Tabs.TabPane>
          </Tabs>
        </div>
      </Collapsible>
    </div>
  );
}
