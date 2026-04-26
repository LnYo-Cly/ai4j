import {
  IReport,
  NodeReport,
  TaskValidateOutput,
  WorkflowInputs,
  WorkflowOutputs,
  WorkflowStatus,
} from '@flowgram.ai/runtime-interface';
import {
  inject,
  injectable,
  WorkflowDocument,
  Playground,
  WorkflowLineEntity,
  WorkflowNodeEntity,
  Emitter,
} from '@flowgram.ai/free-layout-editor';

import { WorkflowRuntimeClient } from '../client';
import { FlowGramTraceView } from '../trace';
import { GetGlobalVariableSchema } from '../../variable-panel-plugin';
import { WorkflowNodeType } from '../../../nodes';
import { serializeWorkflowForBackend } from '../../../utils/backend-workflow';

const SYNC_TASK_REPORT_INTERVAL = 500;

interface NodeRunningStatus {
  nodeID: string;
  status: WorkflowStatus;
  nodeResultLength: number;
}

export type WorkflowRuntimeStatus =
  | 'idle'
  | 'validating'
  | 'running'
  | 'succeeded'
  | 'failed'
  | 'canceled';

export interface WorkflowRuntimeSnapshot {
  taskID?: string;
  inputs: WorkflowInputs;
  validation?: TaskValidateOutput;
  report?: IReport;
  trace?: FlowGramTraceView;
  result?: {
    inputs: WorkflowInputs;
    outputs: WorkflowOutputs;
  };
  errors?: string[];
  status: WorkflowRuntimeStatus;
}

@injectable()
export class WorkflowRuntimeService {
  @inject(Playground) playground: Playground;

  @inject(WorkflowDocument) document: WorkflowDocument;

  @inject(WorkflowRuntimeClient) runtimeClient: WorkflowRuntimeClient;

  @inject(GetGlobalVariableSchema) getGlobalVariableSchema: GetGlobalVariableSchema;

  private runningNodes: WorkflowNodeEntity[] = [];

  private taskID?: string;

  private syncTaskReportIntervalID?: ReturnType<typeof setInterval>;

  private reportEmitter = new Emitter<NodeReport>();

  private resetEmitter = new Emitter<{}>();

  private resultEmitter = new Emitter<{
    errors?: string[];
    result?: {
      inputs: WorkflowInputs;
      outputs: WorkflowOutputs;
    };
  }>();

  private snapshotEmitter = new Emitter<WorkflowRuntimeSnapshot>();

  private nodeRunningStatus = new Map<string, NodeRunningStatus>();

  private draftInputs: WorkflowInputs = {};

  private validationResult?: TaskValidateOutput;

  private latestReport?: IReport;

  private latestTrace?: FlowGramTraceView;

  private latestResult?: {
    inputs: WorkflowInputs;
    outputs: WorkflowOutputs;
  };

  private latestErrors?: string[];

  private runtimeStatus: WorkflowRuntimeStatus = 'idle';

  public onNodeReportChange = this.reportEmitter.event;

  public onReset = this.resetEmitter.event;

  public onResultChanged = this.resultEmitter.event;

  public onSnapshotChanged = this.snapshotEmitter.event;

  public isFlowingLine(line: WorkflowLineEntity) {
    return this.runningNodes.some((node) => node.lines.inputLines.includes(line));
  }

  public getDraftInputs(): WorkflowInputs {
    return this.clone(this.draftInputs);
  }

  public setDraftInputs(inputs: WorkflowInputs): void {
    this.draftInputs = this.clone(inputs ?? {});
    this.emitSnapshot();
  }

  public getSnapshot(): WorkflowRuntimeSnapshot {
    return {
      taskID: this.taskID,
      inputs: this.clone(this.draftInputs),
      validation: this.validationResult,
      report: this.latestReport,
      trace: this.latestTrace,
      result: this.latestResult,
      errors: this.latestErrors,
      status: this.runtimeStatus,
    };
  }

  public async taskValidate(inputs: WorkflowInputs): Promise<TaskValidateOutput | undefined> {
    this.setDraftInputs(inputs);
    this.latestErrors = undefined;
    this.runtimeStatus = 'validating';
    this.emitSnapshot();

    const isFormValid = await this.validateForm();
    if (!isFormValid) {
      const failedValidation: TaskValidateOutput = {
        valid: false,
        errors: ['Form validation failed'],
      };
      this.validationResult = failedValidation;
      this.latestErrors = failedValidation.errors;
      this.runtimeStatus = 'failed';
      this.emitSnapshot();
      return failedValidation;
    }

    const validateResult = await this.runtimeClient.TaskValidate({
      schema: this.buildSchema(),
      inputs: this.getDraftInputs(),
    });

    if (!validateResult) {
      this.validationResult = {
        valid: false,
        errors: ['Validate request failed'],
      };
      this.latestErrors = this.validationResult.errors;
      this.runtimeStatus = 'failed';
      this.emitSnapshot();
      return this.validationResult;
    }

    this.validationResult = validateResult;
    this.latestErrors = validateResult.valid ? undefined : validateResult.errors;
    this.runtimeStatus = validateResult.valid ? (this.taskID ? 'running' : 'idle') : 'failed';
    this.emitSnapshot();
    return validateResult;
  }

  public async taskRun(inputs: WorkflowInputs): Promise<string | undefined> {
    if (this.taskID) {
      await this.taskCancel();
    }

    this.setDraftInputs(inputs);

    const validateResult = await this.taskValidate(inputs);
    if (!validateResult?.valid) {
      this.resultEmitter.fire({
        errors: this.latestErrors ?? ['Validation failed'],
      });
      return;
    }

    this.resetRuntimeState(false);

    let taskID: string | undefined;
    try {
      const output = await this.runtimeClient.TaskRun({
        schema: this.buildSchema(),
        inputs: this.getDraftInputs(),
      });
      taskID = output?.taskID;
    } catch (error) {
      this.latestErrors = [(error as Error)?.message ?? 'Task run failed'];
      this.runtimeStatus = 'failed';
      this.emitSnapshot();
      this.resultEmitter.fire({
        errors: this.latestErrors,
      });
      return;
    }

    if (!taskID) {
      this.latestErrors = ['Task run failed'];
      this.runtimeStatus = 'failed';
      this.emitSnapshot();
      this.resultEmitter.fire({
        errors: this.latestErrors,
      });
      return;
    }

    this.taskID = taskID;
    this.runtimeStatus = 'running';
    this.emitSnapshot();

    this.syncTaskReportIntervalID = setInterval(() => {
      void this.syncTaskReport();
    }, SYNC_TASK_REPORT_INTERVAL);

    return this.taskID;
  }

  public async taskCancel(): Promise<void> {
    if (!this.taskID) {
      return;
    }
    await this.runtimeClient.TaskCancel({
      taskID: this.taskID,
    });
  }

  private buildSchema(): string {
    return serializeWorkflowForBackend({
      ...(this.document.toJSON() as unknown as WorkflowInputs),
      globalVariable: this.getGlobalVariableSchema(),
    } as unknown as import('../../../typings').FlowDocumentJSON);
  }

  private async validateForm(): Promise<boolean> {
    const allForms = this.document.getAllNodes().map((node) => node.form);
    const formValidations = await Promise.all(allForms.map(async (form) => form?.validate()));
    const validations = formValidations.filter((validation) => validation !== undefined);
    return validations.every((validation) => validation);
  }

  private resetRuntimeState(clearInputs: boolean): void {
    this.taskID = undefined;
    this.nodeRunningStatus = new Map<string, NodeRunningStatus>();
    this.runningNodes = [];
    this.latestReport = undefined;
    this.latestTrace = undefined;
    this.latestResult = undefined;
    this.latestErrors = undefined;
    this.runtimeStatus = 'idle';
    this.runtimeClient.clearLatestTrace();

    if (clearInputs) {
      this.draftInputs = {};
      this.validationResult = undefined;
    }

    if (this.syncTaskReportIntervalID) {
      clearInterval(this.syncTaskReportIntervalID);
      this.syncTaskReportIntervalID = undefined;
    }

    this.resetEmitter.fire({});
    this.emitSnapshot();
  }

  private async syncTaskReport(): Promise<void> {
    if (!this.taskID) {
      return;
    }

    const currentTaskID = this.taskID;
    const report = await this.runtimeClient.TaskReport({
      taskID: currentTaskID,
    });

    if (!report) {
      if (this.syncTaskReportIntervalID) {
        clearInterval(this.syncTaskReportIntervalID);
        this.syncTaskReportIntervalID = undefined;
      }
      this.latestErrors = ['Sync task report failed'];
      this.runtimeStatus = 'failed';
      this.emitSnapshot();
      return;
    }

    this.latestReport = report;
    this.latestTrace = this.runtimeClient.getLatestTrace();
    this.updateReport(report);

    if (report.workflowStatus.terminated) {
      if (this.syncTaskReportIntervalID) {
        clearInterval(this.syncTaskReportIntervalID);
        this.syncTaskReportIntervalID = undefined;
      }

      if (report.workflowStatus.status === WorkflowStatus.Succeeded) {
        const outputs = (await this.runtimeClient.TaskResult({
          taskID: currentTaskID,
        })) ?? {};
        this.latestResult = {
          inputs: this.getDraftInputs(),
          outputs,
        };
        this.latestTrace = this.runtimeClient.getLatestTrace() ?? this.latestTrace;
        this.latestErrors = undefined;
        this.runtimeStatus = 'succeeded';
        this.resultEmitter.fire({
          result: this.latestResult,
        });
      } else {
        this.latestResult = undefined;
        this.latestErrors = this.extractErrors(report);
        this.runtimeStatus =
          report.workflowStatus.status === WorkflowStatus.Cancelled ? 'canceled' : 'failed';
        this.resultEmitter.fire({
          errors: this.latestErrors,
        });
      }
    } else {
      this.runtimeStatus = 'running';
    }

    this.emitSnapshot();
  }

  private extractErrors(report: IReport): string[] {
    const workflowErrors = report.messages.error.map((message) =>
      message.nodeID ? `${message.nodeID}: ${message.message}` : message.message
    );

    if (workflowErrors.length > 0) {
      return workflowErrors;
    }

    if (report.workflowStatus.status === WorkflowStatus.Cancelled) {
      return ['Task canceled'];
    }

    return ['Workflow execution failed'];
  }

  private updateReport(report: IReport): void {
    const { reports } = report;
    this.runningNodes = [];

    this.document
      .getAllNodes()
      .filter(
        (node) =>
          ![WorkflowNodeType.BlockStart, WorkflowNodeType.BlockEnd].includes(
            node.flowNodeType as WorkflowNodeType
          )
      )
      .forEach((node) => {
        const nodeID = node.id;
        const nodeReport = reports[nodeID];
        if (!nodeReport) {
          return;
        }
        if (nodeReport.status === WorkflowStatus.Processing) {
          this.runningNodes.push(node);
        }
        const runningStatus = this.nodeRunningStatus.get(nodeID);
        if (
          !runningStatus
          || nodeReport.status !== runningStatus.status
          || nodeReport.snapshots.length !== runningStatus.nodeResultLength
        ) {
          this.nodeRunningStatus.set(nodeID, {
            nodeID,
            status: nodeReport.status,
            nodeResultLength: nodeReport.snapshots.length,
          });
          this.reportEmitter.fire(nodeReport);
          this.document.linesManager.forceUpdate();
        } else if (nodeReport.status === WorkflowStatus.Processing) {
          this.reportEmitter.fire(nodeReport);
        }
      });
  }

  private emitSnapshot(): void {
    this.snapshotEmitter.fire(this.getSnapshot());
  }

  private clone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value ?? {}));
  }
}
