import { nanoid } from 'nanoid';

import { WorkflowNodeType } from '../constants';
import { FlowNodeRegistry } from '../../typings';
import iconKnowledge from '../../assets/icon-knowledge.svg';
import { defaultFormMeta } from '../default-form-meta';

let index = 0;

export const KnowledgeNodeRegistry: FlowNodeRegistry = {
  type: WorkflowNodeType.Knowledge,
  info: {
    icon: iconKnowledge,
    description: 'Retrieve top-K passages from Pinecone with embedding search.',
  },
  meta: {
    size: {
      width: 360,
      height: 390,
    },
  },
  onAdd() {
    return {
      id: `knowledge_${nanoid(5)}`,
      type: WorkflowNodeType.Knowledge,
      data: {
        title: `Knowledge_${++index}`,
        inputsValues: {
          serviceId: {
            type: 'constant',
            content: 'glm-coding',
          },
          embeddingModel: {
            type: 'constant',
            content: 'embedding-3',
          },
          namespace: {
            type: 'constant',
            content: 'default',
          },
          query: {
            type: 'template',
            content: '',
          },
          topK: {
            type: 'constant',
            content: 3,
          },
          delimiter: {
            type: 'constant',
            content: '\n\n',
          },
        },
        inputs: {
          type: 'object',
          required: ['serviceId', 'embeddingModel', 'namespace', 'query'],
          properties: {
            serviceId: {
              type: 'string',
            },
            embeddingModel: {
              type: 'string',
            },
            namespace: {
              type: 'string',
            },
            query: {
              type: 'string',
            },
            topK: {
              type: 'integer',
            },
            delimiter: {
              type: 'string',
            },
          },
        },
        outputs: {
          type: 'object',
          properties: {
            context: {
              type: 'string',
            },
            count: {
              type: 'integer',
            },
            matches: {
              type: 'array',
            },
          },
        },
      },
    };
  },
  formMeta: defaultFormMeta,
};
