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
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("${ai4j.flowgram.api.base-path:/flowgram}")
public class FlowGramTaskController {

    private final FlowGramRuntimeFacade runtimeFacade;

    public FlowGramTaskController(FlowGramRuntimeFacade runtimeFacade) {
        this.runtimeFacade = runtimeFacade;
    }

    @PostMapping("/tasks/run")
    public FlowGramTaskRunResponse run(@RequestBody FlowGramTaskRunRequest request,
                                       WebRequest webRequest) {
        return runtimeFacade.run(request, webRequest);
    }

    @PostMapping("/tasks/validate")
    public FlowGramTaskValidateResponse validate(@RequestBody FlowGramTaskValidateRequest request,
                                                 WebRequest webRequest) {
        return runtimeFacade.validate(request, webRequest);
    }

    @GetMapping("/tasks/{taskId}/report")
    public FlowGramTaskReportResponse report(@PathVariable("taskId") String taskId,
                                             WebRequest webRequest) {
        return runtimeFacade.report(taskId, webRequest);
    }

    @GetMapping("/tasks/{taskId}/result")
    public FlowGramTaskResultResponse result(@PathVariable("taskId") String taskId,
                                             WebRequest webRequest) {
        return runtimeFacade.result(taskId, webRequest);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public FlowGramTaskCancelResponse cancel(@PathVariable("taskId") String taskId,
                                             WebRequest webRequest) {
        return runtimeFacade.cancel(taskId, webRequest);
    }
}
