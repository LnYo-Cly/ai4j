import { FC } from 'react';

import {
  useClientContext,
  useService,
  WorkflowDocument,
  WorkflowNodeEntity,
  WorkflowSelectService,
} from '@flowgram.ai/free-layout-editor';

import { FlowNodeJSON, FlowNodeRegistry } from '../typings';
import { nodeRegistries, WorkflowNodeType } from '../nodes';
import { useNodeFormPanel } from '../plugins/panel-manager-plugin/hooks';
import { workflowTemplates } from '../data/workflow-templates';

interface WorkbenchSidebarProps {
  activeTemplateId?: string;
  onLoadTemplate: (templateId: string) => void;
}

interface WorkflowDocumentWithFactory extends WorkflowDocument {
  createWorkflowNodeByType: (
    type: string,
    position?: unknown,
    nodeJSON?: FlowNodeJSON,
    parentId?: string
  ) => WorkflowNodeEntity;
}

const LOCKED_NODE_TYPES: WorkflowNodeType[] = [WorkflowNodeType.Start, WorkflowNodeType.End];
const ADDABLE_NODE_TYPES: WorkflowNodeType[] = [
  WorkflowNodeType.LLM,
  WorkflowNodeType.HTTP,
  WorkflowNodeType.Code,
  WorkflowNodeType.Tool,
  WorkflowNodeType.Knowledge,
  WorkflowNodeType.Variable,
  WorkflowNodeType.Condition,
  WorkflowNodeType.Loop,
];

const NODE_MATERIAL_META: Record<WorkflowNodeType, { title: string; description: string }> = {
  [WorkflowNodeType.Start]: {
    title: 'Start',
    description: '流程起点。',
  },
  [WorkflowNodeType.End]: {
    title: 'End',
    description: '流程终点。',
  },
  [WorkflowNodeType.LLM]: {
    title: 'LLM',
    description: '调用已配置的大模型，生成文本或结构化回复。',
  },
  [WorkflowNodeType.HTTP]: {
    title: 'HTTP',
    description: '请求外部 REST API，返回 body、headers 与状态码。',
  },
  [WorkflowNodeType.Code]: {
    title: 'Code',
    description: '运行同步 JavaScript，对上游结果做整流与拼装。',
  },
  [WorkflowNodeType.Tool]: {
    title: 'Tool',
    description: '调用本地 ai4j Tool 或 MCP 暴露出来的能力。',
  },
  [WorkflowNodeType.Knowledge]: {
    title: 'Knowledge',
    description: '连接向量检索，为问答流程补充召回片段。',
  },
  [WorkflowNodeType.Variable]: {
    title: 'Variable',
    description: '声明、覆盖或整理变量，供后续节点引用。',
  },
  [WorkflowNodeType.Condition]: {
    title: 'Condition',
    description: '根据条件路由到不同分支，控制下游执行路径。',
  },
  [WorkflowNodeType.Loop]: {
    title: 'Loop',
    description: '批量迭代执行子流程，并对结果做汇总。',
  },
  [WorkflowNodeType.BlockStart]: {
    title: 'Block Start',
    description: '块起点。',
  },
  [WorkflowNodeType.BlockEnd]: {
    title: 'Block End',
    description: '块终点。',
  },
  [WorkflowNodeType.Comment]: {
    title: 'Comment',
    description: '注释。',
  },
  [WorkflowNodeType.Continue]: {
    title: 'Continue',
    description: '继续。',
  },
  [WorkflowNodeType.Break]: {
    title: 'Break',
    description: '中断。',
  },
};

const getRegistry = (type: WorkflowNodeType): FlowNodeRegistry | undefined =>
  nodeRegistries.find((registry) => registry.type === type);

export const WorkbenchSidebar: FC<WorkbenchSidebarProps> = ({
  activeTemplateId,
  onLoadTemplate,
}) => {
  const editorContext = useClientContext();
  const workflowDocument = useService(WorkflowDocument) as WorkflowDocumentWithFactory;
  const selectService = useService(WorkflowSelectService);
  const { open: openNodeForm } = useNodeFormPanel();

  const createNode = (type: WorkflowNodeType) => {
    const registry = getRegistry(type);
    if (!registry?.onAdd) {
      return;
    }

    const node = workflowDocument.createWorkflowNodeByType(
      String(registry.type),
      undefined,
      registry.onAdd(editorContext)
    );
    selectService.selectNode(node);
    openNodeForm({ nodeId: node.id });
  };

  return (
    <aside className="workbench-sidebar">
      <section className="workbench-panel">
        <div className="workbench-panel__label">Templates</div>
        <div className="workbench-panel__title">模板</div>
        <p className="workbench-panel__copy">
          先从现成流程开始，再按需修改节点和参数，会比从纯空白起步更高效。
        </p>
        <div className="workbench-template-list">
          {workflowTemplates.map((template) => (
            <button
              key={template.id}
              type="button"
              className={`workbench-template-card ${
                activeTemplateId === template.id ? 'workbench-template-card--active' : ''
              }`}
              onClick={() => onLoadTemplate(template.id)}
            >
              <span className="workbench-template-card__meta">
                <span className="workbench-template-card__badge">{template.badge ?? 'Template'}</span>
              </span>
              <span className="workbench-template-card__title">{template.name}</span>
              <span className="workbench-template-card__desc">{template.description}</span>
            </button>
          ))}
        </div>
      </section>

      <section className="workbench-panel">
        <div className="workbench-panel__label">Nodes</div>
        <div className="workbench-panel__title">节点素材</div>
        <p className="workbench-panel__copy">
          Start / End 已内置在骨架中，这里保留当前后端已接通的核心业务节点。
        </p>

        <div className="workbench-panel__inline-note">
          <span className="workbench-panel__inline-chip">内置节点</span>
          {LOCKED_NODE_TYPES.map((type) => (
            <span key={type} className="workbench-panel__inline-text">
              {type}
            </span>
          ))}
        </div>

        <div className="workbench-material-grid">
          {ADDABLE_NODE_TYPES.map((type) => {
            const registry = getRegistry(type);
            const meta = NODE_MATERIAL_META[type];
            return (
              <button
                key={type}
                type="button"
                className="workbench-material-card"
                onClick={() => createNode(type)}
              >
                <div className="workbench-material-card__head">
                  {registry?.info?.icon ? (
                    <img className="workbench-material-card__icon" src={registry.info.icon} alt={type} />
                  ) : null}
                  <span className="workbench-material-card__title">{meta?.title ?? type}</span>
                </div>
                <span className="workbench-material-card__desc">
                  {meta?.description ?? registry?.info?.description ?? '添加到当前画布。'}
                </span>
              </button>
            );
          })}
        </div>
      </section>
    </aside>
  );
};
