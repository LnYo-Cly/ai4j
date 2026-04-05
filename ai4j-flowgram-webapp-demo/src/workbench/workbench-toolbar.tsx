import { ChangeEvent, FC, useEffect, useMemo, useRef, useState } from 'react';

import { Button, Tag } from '@douyinfe/semi-ui';
import {
  useClientContext,
  usePlayground,
  usePlaygroundTools,
  useService,
} from '@flowgram.ai/free-layout-editor';

import { WorkflowTemplate } from '../data/workflow-templates';
import { FlowDocumentJSON } from '../typings';
import { useTestRunFormPanel } from '../plugins/panel-manager-plugin/hooks';
import { GetGlobalVariableSchema } from '../plugins/variable-panel-plugin';
import {
  WorkflowRuntimeService,
  WorkflowRuntimeStatus,
} from '../plugins/runtime-plugin/runtime-service';
import { useRuntimeSnapshot } from './runtime-hooks';

interface WorkbenchToolbarProps {
  activeTemplate?: WorkflowTemplate;
  onImportDocument: (document: FlowDocumentJSON) => void;
  onLoadBlank: () => void;
  onLoadDefaultTemplate: () => void;
}

type BackendStatus = 'checking' | 'online' | 'offline';

const HEALTHCHECK_PAYLOAD = {
  schema: {
    nodes: [
      {
        id: 'start_0',
        type: 'Start',
        name: 'start_0',
        data: {
          outputs: {
            type: 'object',
            required: ['message'],
            properties: {
              message: {
                type: 'string',
                default: 'ping',
              },
            },
          },
        },
      },
      {
        id: 'end_0',
        type: 'End',
        name: 'end_0',
        data: {
          inputs: {
            type: 'object',
            required: ['result'],
            properties: {
              result: {
                type: 'string',
              },
            },
          },
          inputsValues: {
            result: {
              type: 'ref',
              content: ['start_0', 'message'],
            },
          },
        },
      },
    ],
    edges: [
      {
        sourceNodeID: 'start_0',
        targetNodeID: 'end_0',
      },
    ],
  },
  inputs: {},
};

const STATUS_LABELS: Record<WorkflowRuntimeStatus, string> = {
  idle: '空闲',
  validating: '校验中',
  running: '运行中',
  succeeded: '成功',
  failed: '失败',
  canceled: '已取消',
};

const downloadTextFile = (filename: string, content: string): void => {
  const blob = new Blob([content], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
};

const isWorkflowDocument = (value: unknown): value is FlowDocumentJSON => {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const workflow = value as FlowDocumentJSON;
  return Array.isArray(workflow.nodes) && Array.isArray(workflow.edges);
};

export const WorkbenchToolbar: FC<WorkbenchToolbarProps> = ({
  activeTemplate,
  onImportDocument,
  onLoadBlank,
  onLoadDefaultTemplate,
}) => {
  const { document } = useClientContext();
  const playground = usePlayground();
  const tools = usePlaygroundTools();
  const runtimeService = useService(WorkflowRuntimeService);
  const getGlobalVariableSchema = useService<GetGlobalVariableSchema>(GetGlobalVariableSchema);
  const { open: openRuntimePanel } = useTestRunFormPanel();
  const snapshot = useRuntimeSnapshot(runtimeService);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking');

  const backendStatusLabel = useMemo(() => {
    if (backendStatus === 'online') {
      return '后端在线';
    }
    if (backendStatus === 'offline') {
      return '后端离线';
    }
    return '检查后端中';
  }, [backendStatus]);

  const templateName = activeTemplate?.name ?? '当前草稿';
  const templateDescription =
    activeTemplate?.description ?? '导入或编辑中的自定义工作流，会自动保存到本地草稿。';
  const templateBadge = activeTemplate?.badge ?? 'Draft';

  useEffect(() => {
    let disposed = false;

    const pingBackend = async () => {
      try {
        const response = await fetch('/flowgram/tasks/validate', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(HEALTHCHECK_PAYLOAD),
        });
        if (!disposed) {
          setBackendStatus(response.ok ? 'online' : 'offline');
        }
      } catch (error) {
        if (!disposed) {
          setBackendStatus('offline');
        }
      }
    };

    pingBackend();
    const timer = window.setInterval(pingBackend, 15000);

    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, []);

  const handleExport = () => {
    const payload = {
      ...document.toJSON(),
      globalVariable: getGlobalVariableSchema(),
    };
    downloadTextFile('ai4j-flowgram-workflow.json', JSON.stringify(payload, null, 2));
  };

  const handleImportChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    try {
      const content = await file.text();
      const parsed = JSON.parse(content) as FlowDocumentJSON;
      if (!isWorkflowDocument(parsed)) {
        throw new Error('文件内容不是有效的 FlowGram workflow JSON。');
      }
      onImportDocument(parsed);
    } catch (error) {
      const message = error instanceof Error ? error.message : '导入 JSON 失败。';
      window.alert(message);
    } finally {
      event.target.value = '';
    }
  };

  const handleValidate = async () => {
    openRuntimePanel();
    await runtimeService.taskValidate(runtimeService.getDraftInputs());
  };

  const handleRun = async () => {
    openRuntimePanel();
    await runtimeService.taskRun(runtimeService.getDraftInputs());
  };

  const handleCancel = async () => {
    await runtimeService.taskCancel();
  };

  const handleAutoLayout = async () => {
    await tools.autoLayout({
      enableAnimation: true,
      animationDuration: 500,
      layoutConfig: {
        rankdir: 'LR',
        align: undefined,
        nodesep: 100,
        ranksep: 100,
      },
    });
    await tools.fitView(false);
    window.scrollTo({
      top: 0,
      left: 0,
      behavior: 'instant',
    });
  };

  return (
    <header className="workbench-toolbar">
      <div className="workbench-toolbar__context">
        <div className="workbench-toolbar__eyebrow">AI4J Flow Studio</div>
        <div className="workbench-toolbar__headline">
          <div className="workbench-toolbar__title">AI4J Flow Studio</div>
          <div className="workbench-toolbar__badge">{templateBadge}</div>
        </div>
        <div className="workbench-toolbar__subtitle">面向 Spring Boot 的可视化工作流编排台</div>
        <div className="workbench-toolbar__template">
          <span className="workbench-toolbar__template-label">当前模板</span>
          <span className="workbench-toolbar__template-name">{templateName}</span>
          <span className="workbench-toolbar__template-copy">{templateDescription}</span>
        </div>
      </div>

      <div className="workbench-toolbar__status">
        <div className="workbench-toolbar__meta">
          <Tag
            color={
              backendStatus === 'online' ? 'green' : backendStatus === 'offline' ? 'red' : 'blue'
            }
          >
            {backendStatusLabel}
          </Tag>
          <Tag color="cyan">代理 /flowgram -&gt; 127.0.0.1:18080</Tag>
          <Tag color="white">运行状态 {STATUS_LABELS[snapshot.status]}</Tag>
          {snapshot.taskID ? <Tag color="white">任务 ID {snapshot.taskID}</Tag> : null}
        </div>
      </div>

      <div className="workbench-toolbar__actions">
        <div className="workbench-toolbar__action-group">
          <Button className="workbench-action workbench-action--secondary" onClick={onLoadBlank}>
            新建空白
          </Button>
          <Button
            className="workbench-action workbench-action--secondary"
            onClick={onLoadDefaultTemplate}
          >
            加载示例
          </Button>
          <Button
            className="workbench-action workbench-action--ghost"
            disabled={playground.config.readonly}
            onClick={() => {
              void handleAutoLayout();
            }}
          >
            自动布局
          </Button>
        </div>
        <div className="workbench-toolbar__action-group">
          <Button className="workbench-action workbench-action--ghost" onClick={handleValidate}>
            校验
          </Button>
          <Button className="workbench-action workbench-action--primary" onClick={handleRun}>
            运行
          </Button>
          <Button
            className="workbench-action workbench-action--ghost"
            disabled={!snapshot.taskID}
            onClick={handleCancel}
          >
            取消
          </Button>
        </div>
        <div className="workbench-toolbar__action-group">
          <Button
            className="workbench-action workbench-action--ghost"
            onClick={() => fileInputRef.current?.click()}
          >
            导入 JSON
          </Button>
          <Button className="workbench-action workbench-action--ghost" onClick={handleExport}>
            导出 JSON
          </Button>
        </div>
        <input
          ref={fileInputRef}
          hidden
          type="file"
          accept="application/json"
          onChange={handleImportChange}
        />
      </div>
    </header>
  );
};
