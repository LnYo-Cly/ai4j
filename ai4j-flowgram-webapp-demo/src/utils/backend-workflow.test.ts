import * as assert from 'node:assert/strict';
import type { WorkflowNodeSchema, WorkflowSchema } from '@flowgram.ai/runtime-interface';

import { normalizeWorkflowForBackend, serializeWorkflowForBackend } from './backend-workflow';

type TestNode = WorkflowNodeSchema & {
  name?: string;
};

const makeNode = (
  id: string,
  type: string,
  data: Record<string, unknown> = {}
): TestNode => ({
  id,
  type,
  meta: {
    position: {
      x: 0,
      y: 0,
    },
  },
  data,
});

const runTest = (name: string, testBody: () => void): void => {
  testBody();
  console.log(`ok - ${name}`);
};

runTest('filters UI-only nodes and invalid edges before sending workflow to backend', () => {
  const source: WorkflowSchema = {
    nodes: [
      makeNode('start_0', 'start', { title: 'Start' }),
      makeNode('llm_0', 'llm', { title: 'Draft answer' }),
      makeNode('comment_0', 'comment', { title: 'Local note' }),
      makeNode('end_0', 'end', { title: 'End' }),
    ],
    edges: [
      { sourceNodeID: 'start_0', targetNodeID: 'llm_0', sourcePortID: 'out', targetPortID: 'in' },
      { sourceNodeID: 'llm_0', targetNodeID: 'comment_0' },
      { sourceNodeID: 'missing', targetNodeID: 'end_0' },
      { sourceNodeID: 'llm_0', targetNodeID: 'end_0' },
    ],
  };

  const normalized = normalizeWorkflowForBackend(source);

  assert.deepEqual(
    normalized.nodes.map((node) => [node.id, node.type, (node as TestNode).name]),
    [
      ['start_0', 'START', 'Start'],
      ['llm_0', 'LLM', 'Draft answer'],
      ['end_0', 'END', 'End'],
    ]
  );
  assert.deepEqual(normalized.edges, [
    { sourceNodeID: 'start_0', targetNodeID: 'llm_0', sourcePortID: 'out', targetPortID: 'in' },
    { sourceNodeID: 'llm_0', targetNodeID: 'end_0' },
  ]);
});

runTest('normalizes loopFor into loop input schema and values without mutating source', () => {
  const loopFor = {
    type: 'ref',
    content: ['start_0', 'cities'],
  };
  const source: WorkflowSchema = {
    nodes: [
      makeNode('loop_0', 'loop', {
        title: 'Loop cities',
        loopFor,
        inputs: {
          type: 'object',
          required: ['existing'],
          properties: {
            existing: {
              type: 'string',
            },
          },
        },
        inputsValues: {
          existing: 'value',
        },
      }),
    ],
    edges: [],
  };

  const normalized = normalizeWorkflowForBackend(source);
  const loopData = normalized.nodes[0].data as Record<string, any>;

  assert.equal(normalized.nodes[0].type, 'LOOP');
  assert.deepEqual(loopData.inputs.required, ['existing', 'loopFor']);
  assert.deepEqual(loopData.inputs.properties.loopFor, { type: 'array' });
  assert.deepEqual(loopData.inputsValues.loopFor, loopFor);
  assert.equal(
    ((source.nodes[0].data.inputs as Record<string, any>).properties as Record<string, unknown>).loopFor,
    undefined
  );
});

runTest('serializes both object and string workflow inputs with backend normalization', () => {
  const source: WorkflowSchema = {
    nodes: [
      makeNode('start_0', 'start', { title: 'Start' }),
      makeNode('variable_0', 'variable', { title: 'Variables' }),
    ],
    edges: [{ sourceNodeID: 'start_0', targetNodeID: 'variable_0' }],
  };

  const fromObject = JSON.parse(serializeWorkflowForBackend(source));
  const fromString = JSON.parse(serializeWorkflowForBackend(JSON.stringify(source)));

  assert.deepEqual(fromString, fromObject);
  assert.deepEqual(
    fromObject.nodes.map((node: WorkflowNodeSchema) => node.type),
    ['START', 'VARIABLE']
  );
});
