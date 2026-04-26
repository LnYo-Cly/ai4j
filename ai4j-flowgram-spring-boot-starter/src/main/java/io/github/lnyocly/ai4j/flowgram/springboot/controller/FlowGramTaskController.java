package io.github.lnyocly.ai4j.flowgram.springboot.controller;

import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskCancelResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskReportResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskResultResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskRunRequest;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskRunResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskValidateRequest;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskValidateResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramRuntimeFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("${ai4j.flowgram.api.base-path:/flowgram}")
public class FlowGramTaskController {

    private final FlowGramRuntimeFacade runtimeFacade;

    public FlowGramTaskController(FlowGramRuntimeFacade runtimeFacade) {
        this.runtimeFacade = runtimeFacade;
    }

    @PostMapping("/tasks/run")
    public FlowGramTaskRunResponse run(@RequestBody FlowGramTaskRunRequest request,
                                       HttpServletRequest servletRequest) {
        return runtimeFacade.run(request, servletRequest);
    }

    @PostMapping("/tasks/validate")
    public FlowGramTaskValidateResponse validate(@RequestBody FlowGramTaskValidateRequest request,
                                                 HttpServletRequest servletRequest) {
        return runtimeFacade.validate(request, servletRequest);
    }

    @GetMapping("/tasks/{taskId}/report")
    public FlowGramTaskReportResponse report(@PathVariable("taskId") String taskId,
                                             HttpServletRequest servletRequest) {
        return runtimeFacade.report(taskId, servletRequest);
    }

    @GetMapping("/tasks/{taskId}/result")
    public FlowGramTaskResultResponse result(@PathVariable("taskId") String taskId,
                                             HttpServletRequest servletRequest) {
        return runtimeFacade.result(taskId, servletRequest);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public FlowGramTaskCancelResponse cancel(@PathVariable("taskId") String taskId,
                                             HttpServletRequest servletRequest) {
        return runtimeFacade.cancel(taskId, servletRequest);
    }
}
