package io.github.lnyocly.ai4j.flowgram.springboot.security;

import org.springframework.web.context.request.WebRequest;

public interface FlowGramCallerResolver {

    FlowGramCaller resolve(WebRequest request);
}
