/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { useCallback, useState } from 'react';

import { FreeLayoutEditorProvider } from '@flowgram.ai/free-layout-editor';

import '@flowgram.ai/free-layout-editor/index.css';
import './styles/index.css';
import { nodeRegistries } from './nodes';
import { useEditorProps } from './hooks';
import { FlowDocumentJSON } from './typings';
import {
  blankWorkflowTemplateId,
  cloneWorkflowTemplate,
  defaultWorkflowTemplateId,
  getWorkflowTemplate,
} from './data/workflow-templates';
import { normalizeVariableNodeOutputs } from './nodes/variable/output-schema';
import { WorkbenchShell } from './workbench/workbench-shell';

const DRAFT_STORAGE_KEY = 'ai4j.flowgram.workbench.draft.v2';
const PROMPT_EDITOR_COMPONENT = 'prompt-editor';

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const sanitizePromptEditorConfig = <T,>(value: T): T => {
  if (Array.isArray(value)) {
    return value.map((item) => sanitizePromptEditorConfig(item)) as T;
  }
  if (!isRecord(value)) {
    return value;
  }
  const nextValue: Record<string, unknown> = {};
  Object.entries(value).forEach(([key, child]) => {
    if (
      key === 'extra' &&
      isRecord(child) &&
      child.formComponent === PROMPT_EDITOR_COMPONENT
    ) {
      const { formComponent, ...rest } = child;
      if (Object.keys(rest).length > 0) {
        nextValue[key] = sanitizePromptEditorConfig(rest);
      }
      return;
    }
    nextValue[key] = sanitizePromptEditorConfig(child);
  });
  return nextValue as T;
};

const sanitizeDocument = (document: FlowDocumentJSON): FlowDocumentJSON =>
  normalizeVariableNodeOutputs(sanitizePromptEditorConfig(document));

const isUsableDocument = (document: unknown): document is FlowDocumentJSON => {
  if (!isRecord(document)) {
    return false;
  }
  const workflow = document as unknown as FlowDocumentJSON;
  return Array.isArray(workflow.nodes) && workflow.nodes.length > 0 && Array.isArray(workflow.edges);
};

export const Editor = () => {
  const [documentSeed, setDocumentSeed] = useState<FlowDocumentJSON>(() => {
    const defaultTemplate = cloneWorkflowTemplate(defaultWorkflowTemplateId);
    const sanitizedDefaultDocument = sanitizeDocument(defaultTemplate.document);
    if (typeof window === 'undefined') {
      return sanitizedDefaultDocument;
    }
    try {
      const saved = window.localStorage.getItem(DRAFT_STORAGE_KEY);
      if (!saved) {
        return sanitizedDefaultDocument;
      }
      const parsed = JSON.parse(saved) as FlowDocumentJSON;
      return isUsableDocument(parsed)
        ? sanitizeDocument(parsed)
        : sanitizedDefaultDocument;
    } catch (error) {
      return sanitizedDefaultDocument;
    }
  });
  const [initialInputs, setInitialInputs] = useState<Record<string, unknown>>(
    () => cloneWorkflowTemplate(defaultWorkflowTemplateId).inputs
  );
  const [activeTemplateId, setActiveTemplateId] = useState<string | undefined>(
    defaultWorkflowTemplateId
  );
  const [editorRevision, setEditorRevision] = useState(0);

  const editorProps = useEditorProps(documentSeed, nodeRegistries);

  const handleDocumentChange = useCallback((document: FlowDocumentJSON) => {
    if (!isUsableDocument(document)) {
      return;
    }
    const sanitizedDocument = sanitizeDocument(document);
    setDocumentSeed(sanitizedDocument);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(sanitizedDocument));
    }
  }, []);

  const handleImportDocument = useCallback((document: FlowDocumentJSON) => {
    const sanitizedDocument = sanitizeDocument(document);
    setDocumentSeed(sanitizedDocument);
    setInitialInputs({});
    setActiveTemplateId(undefined);
    setEditorRevision((revision) => revision + 1);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(sanitizedDocument));
    }
  }, []);

  const handleLoadTemplate = useCallback((templateId: string) => {
    const template = cloneWorkflowTemplate(templateId);
    const sanitizedDocument = sanitizeDocument(template.document);
    setDocumentSeed(sanitizedDocument);
    setInitialInputs(template.inputs);
    setActiveTemplateId(template.id);
    setEditorRevision((revision) => revision + 1);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(sanitizedDocument));
    }
  }, []);

  const handleLoadBlank = useCallback(() => {
    handleLoadTemplate(blankWorkflowTemplateId);
  }, [handleLoadTemplate]);

  const handleLoadDefaultTemplate = useCallback(() => {
    handleLoadTemplate(defaultWorkflowTemplateId);
  }, [handleLoadTemplate]);

  return (
    <div className="doc-free-feature-overview">
      <FreeLayoutEditorProvider key={`editor-${editorRevision}`} {...editorProps}>
        <WorkbenchShell
          activeTemplateId={activeTemplateId}
          activeTemplate={activeTemplateId ? getWorkflowTemplate(activeTemplateId) : undefined}
          initialInputs={initialInputs}
          onDocumentChange={handleDocumentChange}
          onImportDocument={handleImportDocument}
          onLoadBlank={handleLoadBlank}
          onLoadDefaultTemplate={handleLoadDefaultTemplate}
          onLoadTemplate={handleLoadTemplate}
        />
      </FreeLayoutEditorProvider>
    </div>
  );
};
