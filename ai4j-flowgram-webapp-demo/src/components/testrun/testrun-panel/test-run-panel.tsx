/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { FC, useMemo, useState } from 'react';

import { useService } from '@flowgram.ai/free-layout-editor';
import { Button, Switch, Tag } from '@douyinfe/semi-ui';
import { IconClose } from '@douyinfe/semi-icons';

import { TestRunJsonInput } from '../testrun-json-input';
import { TestRunForm } from '../testrun-form';
import { NodeStatusGroup } from '../node-status-bar/group';
import { TracePanel } from '../trace-panel';
import { WorkflowRuntimeService } from '../../../plugins/runtime-plugin/runtime-service';
import { useTestRunFormPanel } from '../../../plugins/panel-manager-plugin/hooks';
import { useRuntimeSnapshot } from '../../../workbench/runtime-hooks';

import styles from './index.module.less';

export interface TestRunSidePanelProps {}

export const TestRunSidePanel: FC<TestRunSidePanelProps> = () => {
  const runtimeService = useService(WorkflowRuntimeService);
  const { close: closePanel } = useTestRunFormPanel();
  const snapshot = useRuntimeSnapshot(runtimeService);
  const [values, setValues] = useState<Record<string, unknown>>(() => runtimeService.getDraftInputs());

  // en - Use localStorage to persist the JSON mode state
  const [inputJSONMode, _setInputJSONMode] = useState(() => {
    const savedMode = localStorage.getItem('testrun-input-json-mode');
    return savedMode ? JSON.parse(savedMode) : false;
  });

  const setInputJSONMode = (checked: boolean) => {
    _setInputJSONMode(checked);
    localStorage.setItem('testrun-input-json-mode', JSON.stringify(checked));
  };

  const onValidate = async () => {
    runtimeService.setDraftInputs(values);
    await runtimeService.taskValidate(values);
  };

  const onRun = async () => {
    runtimeService.setDraftInputs(values);
    if (snapshot.status === 'running') {
      await runtimeService.taskCancel();
      return;
    }
    await runtimeService.taskRun(values);
  };

  const onClose = () => {
    closePanel();
  };

  const reportData = useMemo(() => {
    const reports = snapshot.report?.reports ?? {};
    return Object.fromEntries(
      Object.entries(reports).map(([nodeID, report]) => [
        nodeID,
        {
          status: report.status,
          terminated: report.terminated,
          startTime: report.startTime,
          endTime: report.endTime,
          timeCost: report.timeCost,
        },
      ])
    );
  }, [snapshot.report]);

  const renderForm = (
    <div className={styles['testrun-panel-form']}>
      <div className={styles['runtime-summary']}>
        <div className={styles['runtime-summary-item']}>
          <span className={styles.label}>Task</span>
          <span className={styles.value}>{snapshot.taskID ?? 'Not started'}</span>
        </div>
        <div className={styles['runtime-summary-item']}>
          <span className={styles.label}>Status</span>
          <Tag size="small" color={snapshot.status === 'succeeded' ? 'green' : snapshot.status === 'failed' ? 'red' : snapshot.status === 'running' ? 'blue' : 'white'}>
            {snapshot.status}
          </Tag>
        </div>
        <div className={styles['runtime-summary-item']}>
          <span className={styles.label}>Validation</span>
          <span className={styles.value}>
            {snapshot.validation ? (snapshot.validation.valid ? 'valid' : 'invalid') : 'pending'}
          </span>
        </div>
      </div>

      <div className={styles['testrun-panel-input']}>
        <div className={styles.title}>Input Form</div>
        <div>JSON Mode</div>
        <Switch
          checked={inputJSONMode}
          onChange={(checked: boolean) => setInputJSONMode(checked)}
          size="small"
        />
      </div>
      {inputJSONMode ? (
        <TestRunJsonInput values={values} setValues={setValues} />
      ) : (
        <TestRunForm values={values} setValues={setValues} />
      )}
      {(snapshot.errors ?? []).map((errorMessage) => (
        <div className={styles.error} key={errorMessage}>
          {errorMessage}
        </div>
      ))}
      <NodeStatusGroup title="Draft Inputs" data={values} optional disableCollapse />
      <NodeStatusGroup title="Result Inputs" data={snapshot.result?.inputs} optional disableCollapse />
      <NodeStatusGroup title="Result Outputs" data={snapshot.result?.outputs} optional disableCollapse />
      <NodeStatusGroup title="Node Reports" data={reportData} optional />
      <TracePanel trace={snapshot.trace} taskID={snapshot.taskID} />
    </div>
  );

  return (
    <div className={styles['testrun-panel-container']}>
      <div className={styles['testrun-panel-header']}>
        <div className={styles['testrun-panel-title']}>Test Run</div>
        <Button
          className={styles['testrun-panel-title']}
          type="tertiary"
          icon={<IconClose />}
          size="small"
          theme="borderless"
          onClick={onClose}
        />
      </div>
      <div className={styles['testrun-panel-content']}>
        {renderForm}
      </div>
      <div className={styles['testrun-panel-footer']}>
        <Button className={styles.button} onClick={onValidate}>
          Validate
        </Button>
        <Button className={styles.button} onClick={onRun}>
          {snapshot.status === 'running' ? 'Cancel' : 'Run'}
        </Button>
      </div>
    </div>
  );
};
