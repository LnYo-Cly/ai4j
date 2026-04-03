import { FlowGramNode, WorkflowEdgeSchema, WorkflowNodeSchema, WorkflowSchema } from '@flowgram.ai/runtime-interface';

import { FlowDocumentJSON } from '../typings';

type SerializableWorkflow = string | WorkflowSchema | FlowDocumentJSON;

const UI_ONLY_TYPES = new Set<string>([
  FlowGramNode.Comment,
  FlowGramNode.Group,
  FlowGramNode.BlockStart,
  FlowGramNode.BlockEnd,
]);

const BACKEND_TYPE_MAP: Record<string, string> = {
  [FlowGramNode.Start]: 'START',
  [FlowGramNode.End]: 'END',
  [FlowGramNode.LLM]: 'LLM',
  [FlowGramNode.HTTP]: 'HTTP',
  [FlowGramNode.Code]: 'CODE',
  [FlowGramNode.Condition]: 'CONDITION',
  [FlowGramNode.Loop]: 'LOOP',
  variable: 'VARIABLE',
  tool: 'TOOL',
  knowledge: 'KNOWLEDGE',
};

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value));

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const normalizeType = (type?: string): string => {
  if (!type) {
    return '';
  }
  const lowered = type.trim().toLowerCase();
  return BACKEND_TYPE_MAP[lowered] ?? type;
};

const normalizeLoopNodeData = (data: Record<string, unknown>): Record<string, unknown> => {
  const normalizedData = clone(data);
  const loopFor = normalizedData.loopFor;

  if (loopFor === undefined) {
    return normalizedData;
  }

  const inputs = isRecord(normalizedData.inputs)
    ? clone(normalizedData.inputs)
    : {
        type: 'object',
        required: [] as string[],
        properties: {},
      };

  const properties = isRecord(inputs.properties) ? clone(inputs.properties) : {};
  if (!isRecord(properties.loopFor)) {
    properties.loopFor = {
      type: 'array',
    };
  }

  const required = Array.isArray(inputs.required)
    ? inputs.required.filter((item): item is string => typeof item === 'string')
    : [];
  if (!required.includes('loopFor')) {
    required.push('loopFor');
  }

  const inputsValues = isRecord(normalizedData.inputsValues)
    ? clone(normalizedData.inputsValues)
    : {};
  if (inputsValues.loopFor === undefined) {
    inputsValues.loopFor = clone(loopFor);
  }

  normalizedData.inputs = {
    ...inputs,
    type: 'object',
    required,
    properties,
  };
  normalizedData.inputsValues = inputsValues;

  return normalizedData;
};

const normalizeNodeData = (type: string, data: unknown): Record<string, unknown> => {
  const normalizedData = isRecord(data) ? clone(data) : {};

  if (type === 'Loop') {
    return normalizeLoopNodeData(normalizedData);
  }

  return normalizedData;
};

const normalizeEdges = (
  edges: WorkflowEdgeSchema[] | undefined,
  allowedNodeIds: Set<string>
): WorkflowEdgeSchema[] => {
  if (!Array.isArray(edges)) {
    return [];
  }

  return edges
    .filter(
      (edge): edge is WorkflowEdgeSchema =>
        Boolean(edge?.sourceNodeID)
        && Boolean(edge?.targetNodeID)
        && allowedNodeIds.has(edge.sourceNodeID)
        && allowedNodeIds.has(edge.targetNodeID)
    )
    .map((edge) => ({
      sourceNodeID: edge.sourceNodeID,
      targetNodeID: edge.targetNodeID,
      sourcePortID: edge.sourcePortID,
      targetPortID: edge.targetPortID,
    }));
};

const normalizeNodes = (nodes: WorkflowNodeSchema[] | undefined): WorkflowNodeSchema[] => {
  if (!Array.isArray(nodes)) {
    return [];
  }

  const normalizedNodes: WorkflowNodeSchema[] = [];

  nodes.forEach((node) => {
    if (!node?.id || UI_ONLY_TYPES.has(String(node.type))) {
      return;
    }

    const blocks = normalizeNodes(node.blocks);
    const blockIds = new Set(blocks.map((block) => block.id));
    const normalizedType = normalizeType(String(node.type));

    const normalizedNode = {
      ...node,
      type: normalizedType,
      name: (node as WorkflowNodeSchema & { name?: string }).name ?? node.data?.title ?? node.id,
      data: normalizeNodeData(normalizedType, node.data),
      blocks: blocks.length > 0 ? blocks : undefined,
      edges: blockIds.size > 0 ? normalizeEdges(node.edges, blockIds) : undefined,
    } as WorkflowNodeSchema & {
      name?: string;
    };

    normalizedNodes.push(normalizedNode);
  });

  return normalizedNodes;
};

const parseWorkflow = (schema: SerializableWorkflow): WorkflowSchema => {
  if (typeof schema === 'string') {
    return JSON.parse(schema) as WorkflowSchema;
  }
  return clone(schema as WorkflowSchema);
};

export const normalizeWorkflowForBackend = (
  schema: WorkflowSchema | FlowDocumentJSON
): WorkflowSchema => {
  const parsed = clone(schema as WorkflowSchema);
  const nodes = normalizeNodes(parsed.nodes);
  const nodeIds = new Set(nodes.map((node) => node.id));

  return {
    nodes,
    edges: normalizeEdges(parsed.edges, nodeIds),
  };
};

export const serializeWorkflowForBackend = (schema: SerializableWorkflow): string =>
  JSON.stringify(normalizeWorkflowForBackend(parseWorkflow(schema)));
