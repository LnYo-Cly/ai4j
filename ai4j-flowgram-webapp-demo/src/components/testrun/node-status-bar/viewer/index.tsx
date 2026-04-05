/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import React, { useState } from 'react';

import classnames from 'classnames';
import { Toast } from '@douyinfe/semi-ui';

import styles from './index.module.less';

interface DataStructureViewerProps {
  data: any;
  level?: number;
}

interface TreeNodeProps {
  label: string;
  value: any;
  level: number;
  isLast?: boolean;
}

const TreeNode: React.FC<TreeNodeProps> = ({ label, value, level, isLast = false }) => {
  const [isExpanded, setIsExpanded] = useState(true);

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    Toast.success('Copied');
  };

  const isExpandable = (val: any) =>
    val !== null &&
    typeof val === 'object' &&
    ((Array.isArray(val) && val.length > 0) ||
      (!Array.isArray(val) && Object.keys(val).length > 0));

  const renderPrimitiveValue = (val: any) => {
    if (val === null)
      return <span className={classnames(styles.primitiveValue, styles.null)}>null</span>;
    if (val === undefined)
      return <span className={classnames(styles.primitiveValue, styles.undefined)}>undefined</span>;

    switch (typeof val) {
      case 'string':
        return (
          <span>
            <span className={styles.primitiveValueQuote}>{'"'}</span>
            <span
              className={classnames(styles.primitiveValue, styles.string)}
              onDoubleClick={() => handleCopy(val)}
            >
              {val}
            </span>
            <span className={styles.primitiveValueQuote}>{'"'}</span>
          </span>
        );
      case 'number':
        return (
          <span
            className={classnames(styles.primitiveValue, styles.number)}
            onDoubleClick={() => handleCopy(String(val))}
          >
            {val}
          </span>
        );
      case 'boolean':
        return (
          <span
            className={classnames(styles.primitiveValue, styles.boolean)}
            onDoubleClick={() => handleCopy(val.toString())}
          >
            {val.toString()}
          </span>
        );
      case 'object':
        // Handle empty objects and arrays
        if (Array.isArray(val)) {
          return (
            <span className={styles.primitiveValue} onDoubleClick={() => handleCopy('[]')}>
              []
            </span>
          );
        } else {
          return (
            <span className={styles.primitiveValue} onDoubleClick={() => handleCopy('{}')}>
              {'{}'}
            </span>
          );
        }
      default:
        return (
          <span className={styles.primitiveValue} onDoubleClick={() => handleCopy(String(val))}>
            {String(val)}
          </span>
        );
    }
  };

  const renderChildren = () => {
    if (Array.isArray(value)) {
      return value.map((item, index) => (
        <TreeNode
          key={index}
          label={`${index + 1}.`}
          value={item}
          level={level + 1}
          isLast={index === value.length - 1}
        />
      ));
    } else {
      const entries = Object.entries(value);
      return entries.map(([key, val], index) => (
        <TreeNode
          key={key}
          label={`${key}:`}
          value={val}
          level={level + 1}
          isLast={index === entries.length - 1}
        />
      ));
    }
  };

  return (
    <div className={styles.treeNode}>
      <div className={styles.treeNodeHeader}>
        {isExpandable(value) ? (
          <button
            className={classnames(
              styles.expandButton,
              isExpanded ? styles.expanded : styles.collapsed
            )}
            onClick={() => setIsExpanded(!isExpanded)}
          >
            ▶
          </button>
        ) : (
          <span className={styles.expandPlaceholder}></span>
        )}
        <span
          className={styles.nodeLabel}
          onClick={() =>
            handleCopy(
              JSON.stringify({
                [label]: value,
              })
            )
          }
        >
          {label}
        </span>
        {!isExpandable(value) && (
          <span className={styles.nodeValue}>{renderPrimitiveValue(value)}</span>
        )}
      </div>
      {isExpandable(value) && isExpanded && (
        <div className={styles.treeNodeChildren}>{renderChildren()}</div>
      )}
    </div>
  );
};

export const DataStructureViewer: React.FC<DataStructureViewerProps> = ({ data, level = 0 }) => {
  if (data === null || data === undefined || typeof data !== 'object') {
    return (
      <div className={styles.dataStructureViewer}>
        <TreeNode label="value" value={data} level={0} />
      </div>
    );
  }

  const entries = Object.entries(data);

  return (
    <div className={styles.dataStructureViewer}>
      {entries.map(([key, value], index) => (
        <TreeNode
          key={key}
          label={`${key}:`}
          value={value}
          level={0}
          isLast={index === entries.length - 1}
        />
      ))}
    </div>
  );
};
