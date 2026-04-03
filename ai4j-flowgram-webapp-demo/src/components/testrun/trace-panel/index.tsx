import { FC, useMemo } from 'react';

import { Tabs, Tag } from '@douyinfe/semi-ui';

import {
  FlowGramTraceEventView,
  FlowGramTraceMetricsView,
  FlowGramTraceNodeView,
  FlowGramTraceView,
} from '../../../plugins/runtime-plugin/trace';
import { NodeStatusGroup } from '../node-status-bar/group';

import styles from './index.module.less';

interface TracePanelProps {
  trace?: FlowGramTraceView;
  taskID?: string;
}

const formatTimestamp = (timestamp?: number): string => {
  if (!timestamp) {
    return '--';
  }
  return new Date(timestamp).toLocaleTimeString();
};

const formatDuration = (startedAt?: number, endedAt?: number): string => {
  if (!startedAt) {
    return '--';
  }
  const duration = Math.max((endedAt ?? startedAt) - startedAt, 0);
  if (duration < 1000) {
    return `${duration} ms`;
  }
  return `${(duration / 1000).toFixed(duration >= 10000 ? 0 : 1)} s`;
};

const formatDurationValue = (durationMillis?: number): string => {
  if (durationMillis === undefined || durationMillis === null) {
    return '--';
  }
  if (durationMillis < 1000) {
    return `${durationMillis} ms`;
  }
  return `${(durationMillis / 1000).toFixed(durationMillis >= 10000 ? 0 : 1)} s`;
};

const formatTokens = (metrics?: FlowGramTraceMetricsView): string => {
  if (!metrics) {
    return '--';
  }
  const total = metrics.totalTokens;
  const prompt = metrics.promptTokens;
  const completion = metrics.completionTokens;
  if (total == null && prompt == null && completion == null) {
    return '--';
  }
  const parts = [
    total != null ? `${total} total` : undefined,
    prompt != null ? `${prompt} in` : undefined,
    completion != null ? `${completion} out` : undefined,
  ].filter(Boolean);
  return parts.join(' · ');
};

const formatCost = (metrics?: FlowGramTraceMetricsView): string => {
  if (!metrics || metrics.totalCost === undefined || metrics.totalCost === null) {
    return 'n/a';
  }
  const currency = metrics.currency?.trim();
  const value = metrics.totalCost < 0.01
    ? metrics.totalCost.toFixed(6)
    : metrics.totalCost.toFixed(4);
  return currency ? `${currency} ${value}` : value;
};

const hasUsageMetrics = (metrics?: FlowGramTraceMetricsView): boolean =>
  Boolean(
    metrics
    && (metrics.promptTokens != null
      || metrics.completionTokens != null
      || metrics.totalTokens != null
      || metrics.totalCost != null)
  );

const formatEventType = (type?: string): string =>
  (type ?? 'unknown')
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');

const getStatusColor = (status?: string): 'green' | 'red' | 'blue' | 'grey' | 'white' | 'yellow' => {
  const normalized = status?.trim().toLowerCase();
  switch (normalized) {
    case 'success':
    case 'succeeded':
      return 'green';
    case 'failed':
    case 'error':
      return 'red';
    case 'processing':
    case 'running':
      return 'blue';
    case 'canceled':
    case 'cancelled':
      return 'grey';
    case 'pending':
      return 'yellow';
    default:
      return 'white';
  }
};

const sortEvents = (events: FlowGramTraceEventView[] = []): FlowGramTraceEventView[] =>
  [...events].sort((left, right) => (left.timestamp ?? 0) - (right.timestamp ?? 0));

const sortNodes = (nodes?: Record<string, FlowGramTraceNodeView>): FlowGramTraceNodeView[] =>
  Object.values(nodes ?? {}).sort((left, right) => {
    const timeDelta = (left.startedAt ?? Number.MAX_SAFE_INTEGER) - (right.startedAt ?? Number.MAX_SAFE_INTEGER);
    if (timeDelta !== 0) {
      return timeDelta;
    }
    return (left.nodeId ?? '').localeCompare(right.nodeId ?? '');
  });

export const TracePanel: FC<TracePanelProps> = ({ trace, taskID }) => {
  const events = useMemo(() => sortEvents(trace?.events), [trace?.events]);
  const nodes = useMemo(() => sortNodes(trace?.nodes), [trace?.nodes]);
  const metricsNodes = useMemo(
    () => nodes.filter((node) => node.model || hasUsageMetrics(node.metrics)),
    [nodes]
  );
  const summary = trace?.summary;
  const summaryMetrics = summary?.metrics;

  if (!trace) {
    return (
      <div className={styles['trace-panel']}>
        <div className={styles['trace-panel-head']}>
          <div>
            <div className={styles['trace-panel-title']}>Trace</div>
            <div className={styles['trace-panel-copy']}>
              运行中的任务会在这里展示时间线和节点执行快照。
            </div>
          </div>
        </div>
        <div className={styles['trace-empty']}>
          {taskID
            ? '当前任务还没有返回 trace。请确认后端启用了 ai4j.flowgram.trace-enabled=true。'
            : '先执行一次工作流，面板就会显示任务 trace。'}
        </div>
      </div>
    );
  }

  return (
    <div className={styles['trace-panel']}>
      <div className={styles['trace-panel-head']}>
        <div>
          <div className={styles['trace-panel-title']}>Trace</div>
          <div className={styles['trace-panel-copy']}>
            这一层直接消费后端投影出来的运行时视图，用来检查任务时间线和节点状态。
          </div>
        </div>
        <Tag size="small" color={getStatusColor(trace.status)}>
          {trace.status ?? 'unknown'}
        </Tag>
      </div>

      <div className={styles['trace-summary-grid']}>
        <div className={styles['trace-summary-card']}>
          <div className={styles['trace-summary-label']}>Task</div>
          <div className={styles['trace-summary-value']}>{trace.taskId ?? taskID ?? '--'}</div>
        </div>
        <div className={styles['trace-summary-card']}>
          <div className={styles['trace-summary-label']}>Window</div>
          <div className={styles['trace-summary-value']}>
            {formatTimestamp(trace.startedAt)} - {formatTimestamp(trace.endedAt)}
          </div>
        </div>
        <div className={styles['trace-summary-card']}>
          <div className={styles['trace-summary-label']}>Duration</div>
          <div className={styles['trace-summary-value']}>
            {formatDurationValue(summary?.durationMillis) !== '--'
              ? formatDurationValue(summary?.durationMillis)
              : formatDuration(trace.startedAt, trace.endedAt)}
          </div>
        </div>
        <div className={styles['trace-summary-card']}>
          <div className={styles['trace-summary-label']}>Events / Nodes</div>
          <div className={styles['trace-summary-value']}>
            {summary?.eventCount ?? events.length} / {summary?.nodeCount ?? nodes.length}
          </div>
        </div>
        <div className={styles['trace-summary-card']}>
          <div className={styles['trace-summary-label']}>LLM Tokens</div>
          <div className={styles['trace-summary-value']}>{formatTokens(summaryMetrics)}</div>
        </div>
        <div className={styles['trace-summary-card']}>
          <div className={styles['trace-summary-label']}>Estimated Cost</div>
          <div className={styles['trace-summary-value']}>{formatCost(summaryMetrics)}</div>
        </div>
        <div className={styles['trace-summary-card']}>
          <div className={styles['trace-summary-label']}>Node Health</div>
          <div className={styles['trace-summary-value']}>
            {(summary?.successNodeCount ?? 0)} ok
            {' / '}
            {(summary?.failedNodeCount ?? 0)} failed
          </div>
        </div>
      </div>

      <Tabs className={styles['trace-tabs']} type="card" collapsible>
        <Tabs.TabPane itemKey="metrics" tab={`Metrics (${metricsNodes.length})`}>
          <div className={styles['trace-metrics-summary-grid']}>
            <div className={styles['trace-summary-card']}>
              <div className={styles['trace-summary-label']}>Prompt Tokens</div>
              <div className={styles['trace-summary-value']}>
                {summaryMetrics?.promptTokens ?? '--'}
              </div>
            </div>
            <div className={styles['trace-summary-card']}>
              <div className={styles['trace-summary-label']}>Completion Tokens</div>
              <div className={styles['trace-summary-value']}>
                {summaryMetrics?.completionTokens ?? '--'}
              </div>
            </div>
            <div className={styles['trace-summary-card']}>
              <div className={styles['trace-summary-label']}>Total Tokens</div>
              <div className={styles['trace-summary-value']}>
                {summaryMetrics?.totalTokens ?? '--'}
              </div>
            </div>
            <div className={styles['trace-summary-card']}>
              <div className={styles['trace-summary-label']}>LLM Nodes</div>
              <div className={styles['trace-summary-value']}>
                {summary?.llmNodeCount ?? metricsNodes.length}
              </div>
            </div>
          </div>

          {metricsNodes.length === 0 ? (
            <div className={styles['trace-empty']}>
              当前任务没有可展示的模型 usage 指标。通常意味着流程没有经过 LLM 节点，或者该节点没有返回 usage / cost 数据。
            </div>
          ) : (
            <div className={styles['trace-metrics-list']}>
              {metricsNodes.map((node, index) => (
                <div
                  className={styles['trace-metric-card']}
                  key={`${node.nodeId ?? 'metric'}-${node.startedAt ?? index}-${index}`}
                >
                  <div className={styles['trace-node-head']}>
                    <div>
                      <div className={styles['trace-node-id']}>{node.nodeId ?? 'unknown-node'}</div>
                      {node.model ? (
                        <div className={styles['trace-metric-model']}>{node.model}</div>
                      ) : null}
                    </div>
                    <Tag size="small" color={getStatusColor(node.status)}>
                      {node.status ?? 'unknown'}
                    </Tag>
                  </div>
                  <div className={styles['trace-node-meta']}>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Duration</span>
                      <span className={styles['trace-node-meta-value']}>
                        {formatDurationValue(node.durationMillis)}
                      </span>
                    </div>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Tokens</span>
                      <span className={styles['trace-node-meta-value']}>
                        {formatTokens(node.metrics)}
                      </span>
                    </div>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Estimated Cost</span>
                      <span className={styles['trace-node-meta-value']}>
                        {formatCost(node.metrics)}
                      </span>
                    </div>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Events</span>
                      <span className={styles['trace-node-meta-value']}>
                        {node.eventCount ?? 0} · {node.terminated ? 'terminated' : 'active'}
                      </span>
                    </div>
                  </div>
                  {node.error ? <div className={styles['trace-error']}>{node.error}</div> : null}
                </div>
              ))}
            </div>
          )}
        </Tabs.TabPane>
        <Tabs.TabPane itemKey="timeline" tab={`Timeline (${events.length})`}>
          {events.length === 0 ? (
            <div className={styles['trace-empty']}>当前 trace 还没有事件。</div>
          ) : (
            <div className={styles['trace-events']}>
              {events.map((event, index) => (
                <div
                  className={styles['trace-event-item']}
                  key={`${event.type ?? 'event'}-${event.nodeId ?? 'workflow'}-${event.timestamp ?? index}-${index}`}
                >
                  <div className={styles['trace-event-dot']} />
                  <div>
                    <div className={styles['trace-event-main']}>
                      <span className={styles['trace-event-type']}>{formatEventType(event.type)}</span>
                      {event.nodeId ? (
                        <span className={styles['trace-event-node']}>{event.nodeId}</span>
                      ) : null}
                    </div>
                    <div className={styles['trace-event-meta']}>
                      <Tag size="small" color={getStatusColor(event.status)}>
                        {event.status ?? 'unknown'}
                      </Tag>
                      <span>{formatTimestamp(event.timestamp)}</span>
                      {event.error ? (
                        <span className={styles['trace-error']}>{event.error}</span>
                      ) : null}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Tabs.TabPane>
        <Tabs.TabPane itemKey="nodes" tab={`Nodes (${nodes.length})`}>
          {nodes.length === 0 ? (
            <div className={styles['trace-empty']}>当前 trace 还没有节点明细。</div>
          ) : (
            <div className={styles['trace-node-grid']}>
              {nodes.map((node, index) => (
                <div
                  className={styles['trace-node-card']}
                  key={`${node.nodeId ?? 'node'}-${node.startedAt ?? index}-${index}`}
                >
                  <div className={styles['trace-node-head']}>
                    <div className={styles['trace-node-id']}>{node.nodeId ?? 'unknown-node'}</div>
                    <Tag size="small" color={getStatusColor(node.status)}>
                      {node.status ?? 'unknown'}
                    </Tag>
                  </div>
                  <div className={styles['trace-node-meta']}>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Started</span>
                      <span className={styles['trace-node-meta-value']}>
                        {formatTimestamp(node.startedAt)}
                      </span>
                    </div>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Ended</span>
                      <span className={styles['trace-node-meta-value']}>
                        {formatTimestamp(node.endedAt)}
                      </span>
                    </div>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Duration</span>
                      <span className={styles['trace-node-meta-value']}>
                        {formatDurationValue(node.durationMillis) !== '--'
                          ? formatDurationValue(node.durationMillis)
                          : formatDuration(node.startedAt, node.endedAt)}
                      </span>
                    </div>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Events</span>
                      <span className={styles['trace-node-meta-value']}>
                        {node.eventCount ?? 0} · {node.terminated ? 'terminated' : 'active'}
                      </span>
                    </div>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Model</span>
                      <span className={styles['trace-node-meta-value']}>
                        {node.model ?? '--'}
                      </span>
                    </div>
                    <div className={styles['trace-node-meta-item']}>
                      <span className={styles['trace-node-meta-label']}>Tokens</span>
                      <span className={styles['trace-node-meta-value']}>
                        {formatTokens(node.metrics)}
                      </span>
                    </div>
                  </div>
                  {node.error ? <div className={styles['trace-error']}>{node.error}</div> : null}
                </div>
              ))}
            </div>
          )}
        </Tabs.TabPane>
        <Tabs.TabPane itemKey="raw" tab="Raw">
          <NodeStatusGroup title="Trace JSON" data={trace} disableCollapse />
        </Tabs.TabPane>
      </Tabs>
    </div>
  );
};
