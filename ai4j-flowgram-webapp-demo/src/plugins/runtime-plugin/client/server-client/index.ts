import {
  FlowGramAPIName,
  IReport,
  IRuntimeClient,
  NodeReport,
  Snapshot,
  WorkflowMessageType,
  WorkflowMessages,
  WorkflowInputs,
  WorkflowOutputs,
  WorkflowStatus,
} from '@flowgram.ai/runtime-interface';
import { injectable } from '@flowgram.ai/free-layout-editor';
import { nanoid } from 'nanoid';

import { ServerConfig } from '../../type';
import { FlowGramTraceView } from '../../trace';
import { WorkflowRuntimeClient } from '../base-client';
import type { ServerError } from './type';
import { DEFAULT_SERVER_CONFIG } from './constant';
import { serializeWorkflowForBackend } from '../../../../utils/backend-workflow';

interface BackendTaskRunResponse {
  taskId?: string;
}

interface BackendTaskValidateResponse {
  valid: boolean;
  errors?: string[];
}

interface BackendTaskCancelResponse {
  success?: boolean;
}

interface BackendWorkflowStatus {
  status?: string;
  terminated?: boolean;
  startTime?: number;
  endTime?: number;
  error?: string;
}

interface BackendNodeStatus {
  status?: string;
  terminated?: boolean;
  startTime?: number;
  endTime?: number;
  error?: string;
  inputs?: WorkflowInputs;
  outputs?: WorkflowOutputs;
}

interface BackendTaskReportResponse {
  taskId: string;
  inputs?: WorkflowInputs;
  outputs?: WorkflowOutputs;
  workflow?: BackendWorkflowStatus;
  nodes?: Record<string, BackendNodeStatus>;
  trace?: FlowGramTraceView;
}

type TraceMetrics = NonNullable<NonNullable<FlowGramTraceView['summary']>['metrics']>;
type TraceNodeRecord = NonNullable<FlowGramTraceView['nodes']>;

const toRecord = (value: unknown): Record<string, unknown> | undefined => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return undefined;
  }
  return value as Record<string, unknown>;
};

const pickValue = (source: unknown, ...keys: string[]): unknown => {
  const record = toRecord(source);
  if (!record) {
    return undefined;
  }
  for (const key of keys) {
    if (record[key] !== undefined && record[key] !== null) {
      return record[key];
    }
  }
  return undefined;
};

const toNumber = (value: unknown): number | undefined => {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
};

const toText = (value: unknown): string | undefined => {
  if (value === undefined || value === null) {
    return undefined;
  }
  const text = String(value).trim();
  return text ? text : undefined;
};

const sumMetric = (left?: number, right?: number): number | undefined => {
  if (left === undefined) {
    return right;
  }
  if (right === undefined) {
    return left;
  }
  return left + right;
};

const hasMetricValue = (metrics?: TraceMetrics): boolean =>
  Boolean(
    metrics
    && (metrics.promptTokens !== undefined
      || metrics.completionTokens !== undefined
      || metrics.totalTokens !== undefined
      || metrics.inputCost !== undefined
      || metrics.outputCost !== undefined
      || metrics.totalCost !== undefined)
  );

const buildNodeMetrics = (
  existing: TraceMetrics | undefined,
  nodeStatus?: BackendNodeStatus
): TraceMetrics | undefined => {
  const outputs = toRecord(nodeStatus?.outputs);
  const metrics = pickValue(outputs, 'metrics');
  const rawResponse = pickValue(outputs, 'rawResponse');
  const usage = pickValue(rawResponse, 'usage');

  const promptTokens =
    toNumber(pickValue(existing, 'promptTokens'))
    ?? toNumber(pickValue(metrics, 'promptTokens', 'prompt_tokens'))
    ?? toNumber(pickValue(usage, 'promptTokens', 'prompt_tokens', 'input'));
  const completionTokens =
    toNumber(pickValue(existing, 'completionTokens'))
    ?? toNumber(pickValue(metrics, 'completionTokens', 'completion_tokens'))
    ?? toNumber(pickValue(usage, 'completionTokens', 'completion_tokens', 'output'));
  const totalTokens =
    toNumber(pickValue(existing, 'totalTokens'))
    ?? toNumber(pickValue(metrics, 'totalTokens', 'total_tokens'))
    ?? toNumber(pickValue(usage, 'totalTokens', 'total_tokens', 'total'));
  const inputCost =
    toNumber(pickValue(existing, 'inputCost'))
    ?? toNumber(pickValue(metrics, 'inputCost', 'input_cost'));
  const outputCost =
    toNumber(pickValue(existing, 'outputCost'))
    ?? toNumber(pickValue(metrics, 'outputCost', 'output_cost'));
  const totalCost =
    toNumber(pickValue(existing, 'totalCost'))
    ?? toNumber(pickValue(metrics, 'totalCost', 'total_cost'));
  const currency =
    toText(pickValue(existing, 'currency'))
    ?? toText(pickValue(metrics, 'currency'));

  if (
    promptTokens === undefined
    && completionTokens === undefined
    && totalTokens === undefined
    && inputCost === undefined
    && outputCost === undefined
    && totalCost === undefined
    && currency === undefined
  ) {
    return existing;
  }

  return {
    promptTokens,
    completionTokens,
    totalTokens,
    inputCost,
    outputCost,
    totalCost,
    currency,
  };
};

const buildNodeModel = (
  existingModel: string | undefined,
  nodeStatus?: BackendNodeStatus
): string | undefined =>
  existingModel
  ?? toText(pickValue(pickValue(nodeStatus?.outputs, 'metrics'), 'model'))
  ?? toText(pickValue(pickValue(nodeStatus?.outputs, 'rawResponse'), 'model'))
  ?? toText(pickValue(nodeStatus?.inputs, 'model'))
  ?? toText(pickValue(nodeStatus?.inputs, 'modelName'));

const backfillTraceMetrics = (
  trace: FlowGramTraceView | undefined,
  nodes?: Record<string, BackendNodeStatus>
): FlowGramTraceView | undefined => {
  if (!trace || !nodes) {
    return trace;
  }

  const nextNodes: TraceNodeRecord = {};
  let promptTokens: number | undefined;
  let completionTokens: number | undefined;
  let totalTokens: number | undefined;
  let inputCost: number | undefined;
  let outputCost: number | undefined;
  let totalCost: number | undefined;
  let currency = trace.summary?.metrics?.currency;
  let llmNodeCount = 0;

  Object.entries(trace.nodes ?? {}).forEach(([nodeId, traceNode]) => {
    const nodeStatus = nodes[nodeId];
    const metrics = buildNodeMetrics(traceNode?.metrics, nodeStatus);
    const model = buildNodeModel(traceNode?.model, nodeStatus);
    nextNodes[nodeId] = {
      ...traceNode,
      model,
      metrics,
    };
    if (model || hasMetricValue(metrics)) {
      llmNodeCount += 1;
      promptTokens = sumMetric(promptTokens, metrics?.promptTokens);
      completionTokens = sumMetric(completionTokens, metrics?.completionTokens);
      totalTokens = sumMetric(totalTokens, metrics?.totalTokens);
      inputCost = sumMetric(inputCost, metrics?.inputCost);
      outputCost = sumMetric(outputCost, metrics?.outputCost);
      totalCost = sumMetric(totalCost, metrics?.totalCost);
      currency = currency ?? metrics?.currency;
    }
  });

  return {
    ...trace,
    nodes: nextNodes,
    summary: {
      ...trace.summary,
      llmNodeCount: trace.summary?.llmNodeCount ?? llmNodeCount,
      metrics: {
        ...trace.summary?.metrics,
        promptTokens: trace.summary?.metrics?.promptTokens ?? promptTokens,
        completionTokens: trace.summary?.metrics?.completionTokens ?? completionTokens,
        totalTokens: trace.summary?.metrics?.totalTokens ?? totalTokens,
        inputCost: trace.summary?.metrics?.inputCost ?? inputCost,
        outputCost: trace.summary?.metrics?.outputCost ?? outputCost,
        totalCost: trace.summary?.metrics?.totalCost ?? totalCost,
        currency,
      },
    },
  };
};

const mergeTraceViews = (
  primary: FlowGramTraceView | undefined,
  fallback: FlowGramTraceView | undefined
): FlowGramTraceView | undefined => {
  if (!primary) {
    return fallback;
  }
  if (!fallback) {
    return primary;
  }

  const mergedNodes: TraceNodeRecord = { ...(fallback.nodes ?? {}), ...(primary.nodes ?? {}) };

  Object.entries(mergedNodes).forEach(([nodeId, node]) => {
    const fallbackNode = fallback.nodes?.[nodeId];
    if (!fallbackNode) {
      return;
    }
    mergedNodes[nodeId] = {
      ...fallbackNode,
      ...node,
      model: node?.model ?? fallbackNode.model,
      metrics: {
        ...fallbackNode.metrics,
        ...node?.metrics,
        promptTokens: node?.metrics?.promptTokens ?? fallbackNode.metrics?.promptTokens,
        completionTokens: node?.metrics?.completionTokens ?? fallbackNode.metrics?.completionTokens,
        totalTokens: node?.metrics?.totalTokens ?? fallbackNode.metrics?.totalTokens,
        inputCost: node?.metrics?.inputCost ?? fallbackNode.metrics?.inputCost,
        outputCost: node?.metrics?.outputCost ?? fallbackNode.metrics?.outputCost,
        totalCost: node?.metrics?.totalCost ?? fallbackNode.metrics?.totalCost,
        currency: node?.metrics?.currency ?? fallbackNode.metrics?.currency,
      },
    };
  });

  return {
    ...fallback,
    ...primary,
    nodes: mergedNodes,
    summary: {
      ...fallback.summary,
      ...primary.summary,
      llmNodeCount: primary.summary?.llmNodeCount ?? fallback.summary?.llmNodeCount,
      metrics: {
        ...fallback.summary?.metrics,
        ...primary.summary?.metrics,
        promptTokens: primary.summary?.metrics?.promptTokens ?? fallback.summary?.metrics?.promptTokens,
        completionTokens: primary.summary?.metrics?.completionTokens ?? fallback.summary?.metrics?.completionTokens,
        totalTokens: primary.summary?.metrics?.totalTokens ?? fallback.summary?.metrics?.totalTokens,
        inputCost: primary.summary?.metrics?.inputCost ?? fallback.summary?.metrics?.inputCost,
        outputCost: primary.summary?.metrics?.outputCost ?? fallback.summary?.metrics?.outputCost,
        totalCost: primary.summary?.metrics?.totalCost ?? fallback.summary?.metrics?.totalCost,
        currency: primary.summary?.metrics?.currency ?? fallback.summary?.metrics?.currency,
      },
    },
  };
};

interface BackendTaskResultResponse {
  taskId: string;
  status?: string;
  terminated?: boolean;
  error?: string;
  result?: WorkflowOutputs;
  trace?: FlowGramTraceView;
}

@injectable()
export class WorkflowRuntimeServerClient extends WorkflowRuntimeClient implements IRuntimeClient {
  private config: ServerConfig = DEFAULT_SERVER_CONFIG;

  private latestTrace?: FlowGramTraceView;

  public init(config: ServerConfig) {
    this.config = config;
  }

  public [FlowGramAPIName.TaskRun]: IRuntimeClient[FlowGramAPIName.TaskRun] = async (input) => {
    const normalizedInput = input as {
      schema: string;
      inputs: Record<string, unknown>;
    };

    const output = await this.request<BackendTaskRunResponse>('/flowgram/tasks/run', {
      method: 'POST',
      body: JSON.stringify({
        schema: serializeWorkflowForBackend(normalizedInput.schema),
        inputs: normalizedInput.inputs,
      }),
    });

    if (!output?.taskId) {
      return undefined;
    }

    return {
      taskID: output.taskId,
    };
  };

  public [FlowGramAPIName.TaskReport]: IRuntimeClient[FlowGramAPIName.TaskReport] = async (
    input
  ) => {
    const normalizedInput = input as {
      taskID: string;
    };

    const output = await this.request<BackendTaskReportResponse>(
      `/flowgram/tasks/${normalizedInput.taskID}/report`,
      {
        method: 'GET',
      }
    );

    if (!output) {
      return undefined;
    }

    this.latestTrace = backfillTraceMetrics(output.trace, output.nodes);

    return this.toRuntimeReport(output);
  };

  public [FlowGramAPIName.TaskResult]: IRuntimeClient[FlowGramAPIName.TaskResult] = async (
    input
  ) => {
    const normalizedInput = input as {
      taskID: string;
    };

    const output = await this.request<BackendTaskResultResponse>(
      `/flowgram/tasks/${normalizedInput.taskID}/result`,
      {
        method: 'GET',
      }
    );

    this.latestTrace = mergeTraceViews(output?.trace, this.latestTrace);

    return output?.result;
  };

  public [FlowGramAPIName.TaskCancel]: IRuntimeClient[FlowGramAPIName.TaskCancel] = async (
    input
  ) => {
    const normalizedInput = input as {
      taskID: string;
    };

    const output = await this.request<BackendTaskCancelResponse>(
      `/flowgram/tasks/${normalizedInput.taskID}/cancel`,
      {
        method: 'POST',
      }
    );

    return {
      success: Boolean(output?.success),
    };
  };

  public [FlowGramAPIName.TaskValidate]: IRuntimeClient[FlowGramAPIName.TaskValidate] = async (
    input
  ) => {
    const normalizedInput = input as {
      schema: string;
      inputs: Record<string, unknown>;
    };

    const output = await this.request<BackendTaskValidateResponse>('/flowgram/tasks/validate', {
      method: 'POST',
      body: JSON.stringify({
        schema: serializeWorkflowForBackend(normalizedInput.schema),
        inputs: normalizedInput.inputs,
      }),
    });

    if (!output) {
      return undefined;
    }

    return {
      valid: output.valid,
      errors: output.errors,
    };
  };

  public getLatestTrace(): FlowGramTraceView | undefined {
    return this.latestTrace ? JSON.parse(JSON.stringify(this.latestTrace)) : undefined;
  }

  public clearLatestTrace(): void {
    this.latestTrace = undefined;
  }

  private async request<T>(path: string, init: RequestInit): Promise<T | undefined> {
    try {
      const response = await fetch(this.toUrl(path), {
        ...init,
        headers: {
          'Content-Type': 'application/json',
          ...(init.headers ?? {}),
        },
      });

      if (!response.ok) {
        const errorResponse = await this.safeJson<ServerError>(response);
        console.error('FlowGram request failed', path, response.status, errorResponse);
        return undefined;
      }

      return this.safeJson<T>(response);
    } catch (error) {
      console.error('FlowGram request error', path, error);
      return undefined;
    }
  }

  private async safeJson<T>(response: Response): Promise<T | undefined> {
    try {
      return (await response.json()) as T;
    } catch (error) {
      return undefined;
    }
  }

  private toRuntimeReport(output: BackendTaskReportResponse): IReport {
    const workflowStatus = this.toStatusData(output.workflow);
    const reports: Record<string, NodeReport> = {};

    Object.entries(output.nodes ?? {}).forEach(([nodeID, nodeStatus]) => {
      reports[nodeID] = {
        id: nodeID,
        snapshots: this.toSnapshots(nodeID, nodeStatus),
        ...this.toStatusData(nodeStatus),
      };
    });

    return {
      id: output.taskId,
      inputs: output.inputs ?? {},
      outputs: output.outputs ?? {},
      workflowStatus,
      reports,
      messages: this.toMessages(output),
    };
  }

  private toSnapshots(nodeID: string, nodeStatus?: BackendNodeStatus): Snapshot[] {
    if (!nodeStatus) {
      return [];
    }

    const inputs = nodeStatus.inputs ?? {};
    const outputs = nodeStatus.outputs ?? {};
    const hasInputs = Object.keys(inputs).length > 0;
    const hasOutputs = Object.keys(outputs).length > 0;

    if (!hasInputs && !hasOutputs && !nodeStatus.error) {
      return [];
    }

    return [
      {
        id: nanoid(),
        nodeID,
        inputs,
        outputs,
        data: {},
        ...(nodeStatus.error ? { error: nodeStatus.error } : {}),
      },
    ];
  }

  private toMessages(output: BackendTaskReportResponse): WorkflowMessages {
    const messages: WorkflowMessages = {
      [WorkflowMessageType.Log]: [],
      [WorkflowMessageType.Info]: [],
      [WorkflowMessageType.Debug]: [],
      [WorkflowMessageType.Error]: [],
      [WorkflowMessageType.Warn]: [],
    };

    if (output.workflow?.error) {
      messages.error.push({
        id: nanoid(),
        type: WorkflowMessageType.Error,
        timestamp: output.workflow.endTime ?? output.workflow.startTime ?? Date.now(),
        message: output.workflow.error,
      });
    }

    Object.entries(output.nodes ?? {}).forEach(([nodeID, nodeStatus]) => {
      if (!nodeStatus?.error) {
        return;
      }
      messages.error.push({
        id: nanoid(),
        type: WorkflowMessageType.Error,
        nodeID,
        timestamp: nodeStatus.endTime ?? nodeStatus.startTime ?? Date.now(),
        message: nodeStatus.error,
      });
    });

    return messages;
  }

  private toStatusData(status?: BackendWorkflowStatus | BackendNodeStatus) {
    const mappedStatus = this.mapStatus(status?.status);
    const terminated = status?.terminated ?? this.isTerminal(mappedStatus);
    const startTime = status?.startTime ?? status?.endTime ?? 0;
    const endTime = status?.endTime;
    const timeCost =
      startTime > 0 ? Math.max((endTime ?? startTime) - startTime, 0) : 0;

    return {
      status: mappedStatus,
      terminated,
      startTime,
      endTime,
      timeCost,
    };
  }

  private mapStatus(status?: string): WorkflowStatus {
    const normalized = status?.trim().toLowerCase();
    switch (normalized) {
      case 'success':
      case 'succeeded':
        return WorkflowStatus.Succeeded;
      case 'processing':
      case 'running':
        return WorkflowStatus.Processing;
      case 'failed':
      case 'error':
        return WorkflowStatus.Failed;
      case 'canceled':
      case 'cancelled':
        return WorkflowStatus.Cancelled;
      case 'pending':
      default:
        return WorkflowStatus.Pending;
    }
  }

  private isTerminal(status: WorkflowStatus): boolean {
    return [WorkflowStatus.Succeeded, WorkflowStatus.Failed, WorkflowStatus.Cancelled].includes(
      status
    );
  }

  private toUrl(path: string): string {
    if (!this.config.domain) {
      return path;
    }
    const protocol = this.config.protocol ? `${this.config.protocol}:` : window.location.protocol;
    const host = this.config.port ? `${this.config.domain}:${this.config.port}` : this.config.domain;
    return `${protocol}//${host}${path}`;
  }
}
