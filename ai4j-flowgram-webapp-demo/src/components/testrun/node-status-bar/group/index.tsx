/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { FC, useState } from 'react';

import classNames from 'classnames';
import { Tag } from '@douyinfe/semi-ui';
import { IconSmallTriangleDown } from '@douyinfe/semi-icons';

import { DataStructureViewer } from '../viewer';

import styles from './index.module.less';

interface NodeStatusGroupProps {
  title: string;
  data: unknown;
  optional?: boolean;
  disableCollapse?: boolean;
}

const isObjectHasContent = (obj: any = {}): boolean => obj && Object.keys(obj).length > 0;

export const NodeStatusGroup: FC<NodeStatusGroupProps> = ({
  title,
  data,
  optional = false,
  disableCollapse = false,
}) => {
  const hasContent = isObjectHasContent(data);
  const [isExpanded, setIsExpanded] = useState(true);

  if (optional && !hasContent) {
    return null;
  }

  return (
    <>
      <div
        className={styles['node-status-group']}
        onClick={() => hasContent && !disableCollapse && setIsExpanded(!isExpanded)}
      >
        {!disableCollapse && (
          <IconSmallTriangleDown
            className={classNames(styles['node-status-group-icon'], {
              [styles['node-status-group-icon-expanded']]: isExpanded && hasContent,
            })}
          />
        )}
        <span>{title}:</span>
        {!hasContent && (
          <Tag size="small" className={styles['node-status-group-tag']}>
            null
          </Tag>
        )}
      </div>
      {hasContent && isExpanded ? <DataStructureViewer data={data} /> : null}
    </>
  );
};
