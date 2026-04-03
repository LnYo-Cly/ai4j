package io.github.lnyocly.ai4j.flowgram.springboot.support;

import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeService;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskCancelOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskReportOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskResultOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskRunOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskValidateOutput;
import io.github.lnyocly.ai4j.flowgram.springboot.adapter.FlowGramProtocolAdapter;
import io.github.lnyocly.ai4j.flowgram.springboot.config.FlowGramProperties;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskCancelResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskReportResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskResultResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskRunRequest;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskRunResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskValidateRequest;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskValidateResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTraceView;
import io.github.lnyocly.ai4j.flowgram.springboot.exception.FlowGramAccessDeniedException;
import io.github.lnyocly.ai4j.flowgram.springboot.exception.FlowGramTaskNotFoundException;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramAccessChecker;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramAction;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramCaller;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramCallerResolver;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramTaskOwnership;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramTaskOwnershipStrategy;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

public class FlowGramRuntimeFacade {

    private final FlowGramRuntimeService runtimeService;
    private final FlowGramProtocolAdapter protocolAdapter;
    private final FlowGramTaskStore taskStore;
    private final FlowGramCallerResolver callerResolver;
    private final FlowGramAccessChecker accessChecker;
    private final FlowGramTaskOwnershipStrategy ownershipStrategy;
    private final FlowGramProperties properties;
    private final FlowGramRuntimeTraceCollector traceCollector;
    private final FlowGramTraceResponseEnricher traceResponseEnricher = new FlowGramTraceResponseEnricher();

    public FlowGramRuntimeFacade(FlowGramRuntimeService runtimeService,
                                 FlowGramProtocolAdapter protocolAdapter,
                                 FlowGramTaskStore taskStore,
                                 FlowGramCallerResolver callerResolver,
                                 FlowGramAccessChecker accessChecker,
                                 FlowGramTaskOwnershipStrategy ownershipStrategy,
                                 FlowGramProperties properties,
                                 FlowGramRuntimeTraceCollector traceCollector) {
        this.runtimeService = runtimeService;
        this.protocolAdapter = protocolAdapter;
        this.taskStore = taskStore;
        this.callerResolver = callerResolver;
        this.accessChecker = accessChecker;
        this.ownershipStrategy = ownershipStrategy;
        this.properties = properties;
        this.traceCollector = traceCollector;
    }

    public FlowGramTaskRunResponse run(FlowGramTaskRunRequest request, HttpServletRequest servletRequest) {
        FlowGramCaller caller = resolveCaller(servletRequest);
        ensureAllowed(FlowGramAction.RUN, caller, null);
        FlowGramTaskRunOutput output = runtimeService.runTask(protocolAdapter.toTaskRunInput(request));
        FlowGramTaskOwnership ownership = ownershipStrategy.createOwnership(output.getTaskID(), caller);
        taskStore.save(FlowGramStoredTask.builder()
                .taskId(output.getTaskID())
                .creatorId(ownership == null ? null : ownership.getCreatorId())
                .tenantId(ownership == null ? null : ownership.getTenantId())
                .createdAt(ownership == null ? null : ownership.getCreatedAt())
                .expiresAt(ownership == null ? null : ownership.getExpiresAt())
                .status("pending")
                .terminated(false)
                .resultSnapshot(Collections.<String, Object>emptyMap())
                .build());
        return protocolAdapter.toRunResponse(output);
    }

    public FlowGramTaskValidateResponse validate(FlowGramTaskValidateRequest request, HttpServletRequest servletRequest) {
        FlowGramCaller caller = resolveCaller(servletRequest);
        ensureAllowed(FlowGramAction.VALIDATE, caller, null);
        FlowGramTaskValidateOutput output = runtimeService.validateTask(protocolAdapter.toTaskRunInput(request));
        return protocolAdapter.toValidateResponse(output);
    }

    public FlowGramTaskReportResponse report(String taskId, HttpServletRequest servletRequest) {
        FlowGramTaskReportOutput report = runtimeService.getTaskReport(taskId);
        if (report == null) {
            throw new FlowGramTaskNotFoundException(taskId);
        }
        FlowGramStoredTask task = loadTask(taskId);
        FlowGramCaller caller = resolveCaller(servletRequest);
        ensureAllowed(FlowGramAction.REPORT, caller, task);
        if (report.getWorkflow() != null) {
            taskStore.updateState(taskId,
                    report.getWorkflow().getStatus(),
                    report.getWorkflow().isTerminated(),
                    report.getWorkflow().getError(),
                    null);
        }
        return traceResponseEnricher.enrichReportResponse(protocolAdapter.toReportResponse(taskId,
                report,
                properties == null || properties.isReportNodeDetails(),
                resolveTrace(taskId, report)));
    }

    public FlowGramTaskResultResponse result(String taskId, HttpServletRequest servletRequest) {
        FlowGramTaskResultOutput result = runtimeService.getTaskResult(taskId);
        if (result == null) {
            throw new FlowGramTaskNotFoundException(taskId);
        }
        FlowGramStoredTask task = loadTask(taskId);
        FlowGramCaller caller = resolveCaller(servletRequest);
        ensureAllowed(FlowGramAction.RESULT, caller, task);
        taskStore.updateState(taskId, result.getStatus(), result.isTerminated(), result.getError(), result.getResult());
        FlowGramTaskReportOutput report = runtimeService.getTaskReport(taskId);
        FlowGramTraceView trace = resolveTrace(taskId, report);
        FlowGramTaskReportResponse reportResponse = traceResponseEnricher.enrichReportResponse(
                protocolAdapter.toReportResponse(taskId, report, true, trace));
        return protocolAdapter.toResultResponse(taskId, result, reportResponse == null ? trace : reportResponse.getTrace());
    }

    public FlowGramTaskCancelResponse cancel(String taskId, HttpServletRequest servletRequest) {
        FlowGramStoredTask task = loadTask(taskId);
        FlowGramCaller caller = resolveCaller(servletRequest);
        ensureAllowed(FlowGramAction.CANCEL, caller, task);
        FlowGramTaskCancelOutput output = runtimeService.cancelTask(taskId);
        if (output == null || !output.isSuccess()) {
            throw new FlowGramTaskNotFoundException(taskId);
        }
        return protocolAdapter.toCancelResponse(true);
    }

    private FlowGramCaller resolveCaller(HttpServletRequest servletRequest) {
        FlowGramCaller caller = callerResolver.resolve(servletRequest);
        return caller == null ? FlowGramCaller.builder().callerId("anonymous").anonymous(true).build() : caller;
    }

    private FlowGramStoredTask loadTask(String taskId) {
        FlowGramStoredTask task = taskStore.find(taskId);
        if (task != null) {
            return task;
        }
        return FlowGramStoredTask.builder().taskId(taskId).build();
    }

    private void ensureAllowed(FlowGramAction action, FlowGramCaller caller, FlowGramStoredTask task) {
        if (!accessChecker.isAllowed(action, caller, task)) {
            throw new FlowGramAccessDeniedException(action);
        }
    }

    private FlowGramTraceView resolveTrace(String taskId, FlowGramTaskReportOutput report) {
        if (traceCollector == null || (properties != null && !properties.isTraceEnabled())) {
            return null;
        }
        return traceCollector.getTrace(taskId, report);
    }
}
