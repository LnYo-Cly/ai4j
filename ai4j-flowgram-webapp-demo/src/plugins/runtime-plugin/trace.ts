export interface FlowGramTraceEventView {
  type?: string;
  timestamp?: number;
  nodeId?: string;
  status?: string;
  error?: string;
}

export interface FlowGramTraceNodeView {
  nodeId?: string;
  status?: string;
  terminated?: boolean;
  startedAt?: number;
  endedAt?: number;
  durationMillis?: number;
  error?: string;
  eventCount?: number;
  model?: string;
  metrics?: FlowGramTraceMetricsView;
}

export interface FlowGramTraceMetricsView {
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  inputCost?: number;
  outputCost?: number;
  totalCost?: number;
  currency?: string;
}

export interface FlowGramTraceSummaryView {
  durationMillis?: number;
  eventCount?: number;
  nodeCount?: number;
  terminatedNodeCount?: number;
  successNodeCount?: number;
  failedNodeCount?: number;
  llmNodeCount?: number;
  metrics?: FlowGramTraceMetricsView;
}

export interface FlowGramTraceView {
  taskId?: string;
  status?: string;
  startedAt?: number;
  endedAt?: number;
  summary?: FlowGramTraceSummaryView;
  events?: FlowGramTraceEventView[];
  nodes?: Record<string, FlowGramTraceNodeView>;
}
