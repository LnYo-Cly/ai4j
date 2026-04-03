import { FC, useEffect, useRef, useState } from 'react';

import {
  EditorRenderer,
  usePlaygroundTools,
  useService,
  WorkflowDocument,
} from '@flowgram.ai/free-layout-editor';
import { DockedPanelLayer } from '@flowgram.ai/panel-manager-plugin';

import { FlowDocumentJSON } from '../typings';
import { blankWorkflowTemplateId, WorkflowTemplate } from '../data/workflow-templates';
import { WorkflowNodeType } from '../nodes';
import { GetGlobalVariableSchema } from '../plugins/variable-panel-plugin';
import { WorkflowRuntimeService } from '../plugins/runtime-plugin/runtime-service';
import { WorkbenchSidebar } from './workbench-sidebar';
import { WorkbenchToolbar } from './workbench-toolbar';

interface WorkbenchShellProps {
  activeTemplateId?: string;
  activeTemplate?: WorkflowTemplate;
  initialInputs: Record<string, unknown>;
  onDocumentChange: (document: FlowDocumentJSON) => void;
  onImportDocument: (document: FlowDocumentJSON) => void;
  onLoadBlank: () => void;
  onLoadDefaultTemplate: () => void;
  onLoadTemplate: (templateId: string) => void;
}

const hasRenderableNodes = (document: FlowDocumentJSON): boolean =>
  Array.isArray(document.nodes) && document.nodes.length > 0;

const isBlankCanvasDraft = (document: FlowDocumentJSON): boolean => {
  const nodes = Array.isArray(document.nodes)
    ? document.nodes.filter((node) => node.type !== WorkflowNodeType.Comment)
    : [];
  return (
    nodes.length === 2 &&
    nodes.every(
      (node) => node.type === WorkflowNodeType.Start || node.type === WorkflowNodeType.End
    )
  );
};

const WorkbenchAutosaveBridge: FC<{
  onDocumentChange: (document: FlowDocumentJSON) => void;
}> = ({ onDocumentChange }) => {
  const document = useService(WorkflowDocument);
  const getGlobalVariableSchema = useService<GetGlobalVariableSchema>(GetGlobalVariableSchema);

  useEffect(() => {
    const persist = () => {
      const payload = {
        ...document.toJSON(),
        globalVariable: getGlobalVariableSchema(),
      } as unknown as FlowDocumentJSON;
      if (!hasRenderableNodes(payload)) {
        return;
      }
      onDocumentChange(payload);
    };

    persist();
    const disposable = document.onContentChange(() => {
      persist();
    });

    return () => disposable.dispose();
  }, [document, getGlobalVariableSchema, onDocumentChange]);

  return null;
};

const WorkbenchViewportBridge: FC = () => {
  const document = useService(WorkflowDocument);
  const { fitView } = usePlaygroundTools();
  const fittedRef = useRef(false);

  useEffect(() => {
    const fitToContent = () => {
      if (fittedRef.current || document.root.blocks.length === 0) {
        return;
      }
      fittedRef.current = true;
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          void fitView(false);
          window.scrollTo({
            top: 0,
            left: 0,
            behavior: 'instant',
          });
        });
      });
    };

    fitToContent();
    const disposable = document.onContentChange(() => {
      fitToContent();
    });

    return () => disposable.dispose();
  }, [document, fitView]);

  return null;
};

const WorkbenchBlankStateOverlay: FC<{
  activeTemplateId?: string;
  onLoadDefaultTemplate: () => void;
}> = ({ activeTemplateId, onLoadDefaultTemplate }) => {
  const document = useService(WorkflowDocument);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const sync = () => {
      const payload = document.toJSON() as FlowDocumentJSON;
      setVisible(activeTemplateId === blankWorkflowTemplateId && isBlankCanvasDraft(payload));
    };

    sync();
    const disposable = document.onContentChange(() => {
      sync();
    });

    return () => disposable.dispose();
  }, [activeTemplateId, document]);

  if (!visible) {
    return null;
  }

  return (
    <div className="workbench-empty-state">
      <div className="workbench-empty-state__label">Blank Canvas</div>
      <div className="workbench-empty-state__title">从空白流程开始</div>
      <p className="workbench-empty-state__copy">
        从左侧拖入节点，逐步搭建你的工作流。若想快速体验运行链路，建议先加载默认示例。
      </p>
      <div className="workbench-empty-state__actions">
        <button
          type="button"
          className="workbench-empty-state__button workbench-empty-state__button--primary"
          onClick={onLoadDefaultTemplate}
        >
          加载默认示例
        </button>
      </div>
    </div>
  );
};

export const WorkbenchShell: FC<WorkbenchShellProps> = ({
  activeTemplateId,
  activeTemplate,
  initialInputs,
  onDocumentChange,
  onImportDocument,
  onLoadBlank,
  onLoadDefaultTemplate,
  onLoadTemplate,
}) => {
  const runtimeService = useService(WorkflowRuntimeService);

  useEffect(() => {
    runtimeService.setDraftInputs(initialInputs);
  }, [initialInputs, runtimeService]);

  return (
    <>
      <WorkbenchAutosaveBridge onDocumentChange={onDocumentChange} />
      <WorkbenchViewportBridge />
      <div className="workbench-shell">
        <WorkbenchToolbar
          activeTemplate={activeTemplate}
          onImportDocument={onImportDocument}
          onLoadBlank={onLoadBlank}
          onLoadDefaultTemplate={onLoadDefaultTemplate}
        />
        <div className="workbench-body">
          <WorkbenchSidebar activeTemplateId={activeTemplateId} onLoadTemplate={onLoadTemplate} />
          <section className="workbench-stage">
            <div className="workbench-stage__header">
              <div className="workbench-stage__summary">
                <div className="workbench-stage__label">Canvas</div>
                <div className="workbench-stage__copy">
                  {activeTemplate ? `当前画布：${activeTemplate.name}` : '当前草稿'}
                  {' · '}拖拽节点后会自动保存到本地草稿
                </div>
              </div>
              <div className="workbench-stage__hint">点击节点后，在右侧表单继续配置参数与输入输出</div>
            </div>
            <div className="workbench-stage__canvas">
              <DockedPanelLayer>
                <EditorRenderer className="demo-editor" />
              </DockedPanelLayer>
              <WorkbenchBlankStateOverlay
                activeTemplateId={activeTemplateId}
                onLoadDefaultTemplate={onLoadDefaultTemplate}
              />
            </div>
          </section>
        </div>
      </div>
    </>
  );
};
