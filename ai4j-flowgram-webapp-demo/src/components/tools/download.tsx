/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useEffect, useState, type FC } from 'react';

import { usePlayground, useService } from '@flowgram.ai/free-layout-editor';
import { FlowDownloadFormat, FlowDownloadService } from '@flowgram.ai/export-plugin';
import { IconButton, Toast, Dropdown, Tooltip } from '@douyinfe/semi-ui';
import { IconFilledArrowDown } from '@douyinfe/semi-icons';

const formatOptions = [
  {
    label: 'PNG',
    value: FlowDownloadFormat.PNG,
  },
  {
    label: 'JPEG',
    value: FlowDownloadFormat.JPEG,
  },
  {
    label: 'SVG',
    value: FlowDownloadFormat.SVG,
  },
  {
    label: 'JSON',
    value: FlowDownloadFormat.JSON,
  },
  {
    label: 'YAML',
    value: FlowDownloadFormat.YAML,
  },
];

export const DownloadTool: FC = () => {
  const [downloading, setDownloading] = useState<boolean>(false);
  const [visible, setVisible] = useState(false);
  const playground = usePlayground();
  const { readonly } = playground.config;
  const downloadService = useService(FlowDownloadService);

  useEffect(() => {
    const subscription = downloadService.onDownloadingChange((v) => {
      setDownloading(v);
    });

    return () => {
      subscription.dispose();
    };
  }, [downloadService]);

  const handleDownload = async (format: FlowDownloadFormat) => {
    setVisible(false);
    await downloadService.download({
      format,
    });
    const formatOption = formatOptions.find((option) => option.value === format);
    Toast.success(`Download ${formatOption?.label} successfully`);
  };

  const button = (
    <IconButton
      type="tertiary"
      theme="borderless"
      className={visible ? '!coz-mg-secondary-pressed' : undefined}
      icon={<IconFilledArrowDown />}
      loading={downloading}
      onClick={() => setVisible(true)}
    />
  );

  return (
    <Dropdown
      trigger="custom"
      visible={visible}
      position="topLeft"
      onClickOutSide={() => setVisible(false)}
      render={
        <Dropdown.Menu className="min-w-[120px]">
          {formatOptions.map((item) => (
            <Dropdown.Item
              disabled={downloading || readonly}
              key={item.value}
              onClick={() => handleDownload(item.value)}
            >
              {item.label}
            </Dropdown.Item>
          ))}
        </Dropdown.Menu>
      }
    >
      {visible ? (
        button
      ) : (
        <div>
          <Tooltip content="Download">{button}</Tooltip>
        </div>
      )}
    </Dropdown>
  );
};
