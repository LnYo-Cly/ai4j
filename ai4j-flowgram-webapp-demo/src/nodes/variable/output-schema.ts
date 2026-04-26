import { FlowValueUtils } from '@flowgram.ai/form-materials';
import type { AssignValueType, IFlowValue } from '@flowgram.ai/form-materials';

import type { JsonSchema } from '../../typings/json-schema';
import type { FlowDocumentJSON, FlowNodeJSON } from '../../typings/node';
import { WorkflowNodeType } from '../constants';

const cloneSchema = <T,>(value: T): T => JSON.parse(JSON.stringify(value));

const resolveSchemaByPath = (schema: JsonSchema | undefined, path: string[]): JsonSchema | undefined => {
  let current: JsonSchema | undefined = schema;
  for (const segment of path) {
    if (!current || current.type !== 'object') {
      return undefined;
    }
    const properties = current.properties as Record<string, JsonSchema> | undefined;
    current = properties?.[segment];
  }
  return current ? cloneSchema(current) : undefined;
};

const inferValueSchema = (
  value: IFlowValue | undefined,
  nodeIndex: Map<string, FlowNodeJSON>
): JsonSchema | undefined => {
  if (!value) {
    return undefined;
  }
  if (FlowValueUtils.isConstant(value)) {
    return FlowValueUtils.inferConstantJsonSchema(value);
  }
  if (FlowValueUtils.isTemplate(value) || FlowValueUtils.isExpression(value)) {
    return { type: 'string' };
  }
  if (!FlowValueUtils.isRef(value)) {
    return undefined;
  }

  const keyPath = value.content ?? [];
  if (keyPath.length === 0) {
    return undefined;
  }
  const [nodeId, ...path] = keyPath;
  const targetNode = nodeIndex.get(nodeId);
  return resolveSchemaByPath(targetNode?.data?.outputs, path);
};

const normalizeAssignValueSchema = (value: IFlowValue | undefined): void => {
  if (!value || !FlowValueUtils.isConstant(value) || value.schema) {
    return;
  }
  const inferredSchema = FlowValueUtils.inferConstantJsonSchema(value);
  if (inferredSchema) {
    value.schema = inferredSchema as JsonSchema;
  }
};

const normalizeVariableAssignRows = (assign: AssignValueType[] | undefined): void => {
  for (const row of assign ?? []) {
    normalizeAssignValueSchema(row.right);
  }
};

export const inferVariableOutputsSchema = (
  assign: AssignValueType[] | undefined,
  nodeIndex: Map<string, FlowNodeJSON>
): JsonSchema => {
  const properties: Record<string, JsonSchema> = {};
  const required: string[] = [];

  for (const row of assign ?? []) {
    if (row.operator !== 'declare' || !row.left) {
      continue;
    }
    const key = row.left.trim();
    if (!key) {
      continue;
    }
    const schema = inferValueSchema(row.right, nodeIndex) ?? { type: 'string' };
    properties[key] = schema;
    required.push(key);
  }

  return {
    type: 'object',
    required,
    properties,
  };
};

export const normalizeVariableNodeOutputs = (document: FlowDocumentJSON): FlowDocumentJSON => {
  const nodeIndex = new Map<string, FlowNodeJSON>();
  for (const node of document.nodes) {
    nodeIndex.set(node.id, node);
  }

  for (let pass = 0; pass < document.nodes.length; pass += 1) {
    let changed = false;
    for (const node of document.nodes) {
      if (node.type !== WorkflowNodeType.Variable) {
        continue;
      }
      normalizeVariableAssignRows(node.data?.assign);
      const nextOutputs = inferVariableOutputsSchema(node.data?.assign, nodeIndex);
      const prevOutputs = node.data?.outputs;
      if (JSON.stringify(prevOutputs ?? null) === JSON.stringify(nextOutputs)) {
        continue;
      }
      node.data.outputs = nextOutputs;
      changed = true;
    }
    if (!changed) {
      break;
    }
  }

  return document;
};
